package com.example.amirha.accessiblevd;

import android.app.Activity;
import android.app.Presentation;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.Display;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final AccessibleTextureView textureView = new AccessibleTextureView(this);
        setFrameLayoutParams(textureView, Gravity.CENTER);

        // Boilerplate to create a virtual display that renders to the TextureView's texture.
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
                int densityDpi = getResources().getDisplayMetrics().densityDpi;
                VirtualDisplay virtualDisplay = displayManager.createVirtualDisplay(
                        "vd",
                        width,
                        height,
                        densityDpi,
                        new Surface(surface),
                        0
                );
                SimplePresentation simplePresentation = new SimplePresentation(MainActivity.this, virtualDisplay.getDisplay(), textureView);
                simplePresentation.show();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });

        FrameLayout container = new FrameLayout(this);
        container.setBackgroundColor(0xff00ff00);
        container.addView(createView("Top text", Gravity.TOP));
        container.addView(textureView);
        container.addView(createView("Bottom text", Gravity.BOTTOM));
        setContentView(container);
    }

    private View createView(final String text, int gravity) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d("AXVD","clicked out of vd: " + text);
            }
        });
        setFrameLayoutParams(button, gravity);
        return button;
    }

    private static void setFrameLayoutParams(View view, int gravity) {
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(400, 400);
        layoutParams.gravity = gravity;
        view.setLayoutParams(layoutParams);
    }
}

class SimplePresentation extends Presentation {

    private Button embeddedView;
    private AccessibleTextureView accessibleTextureView;

    public SimplePresentation(
            Context outerContext,
            Display display, AccessibleTextureView accessibleTextureView) {
        super(outerContext, display);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        );
        this.accessibleTextureView = accessibleTextureView;
    }

    private static void setFullScreen(Window window) {
        window.setFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS, WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(0x00000000);
        }
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setFullScreen(getWindow());
        embeddedView = new Button(getContext()) {
            public int getAccessibilityWindowId() {
                return accessibleTextureView.createAccessibilityNodeInfo().getWindowId();
            }
        };
        embeddedView.setText("Hello world!");
        embeddedView.setBackgroundColor(0xffff0000);
        embeddedView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d("AXVD", "clicked on vd button");
            }
        });
        AccessibilityDelegatingFrameLayout container = new AccessibilityDelegatingFrameLayout(getContext(), accessibleTextureView);
        container.addView(embeddedView);
        setContentView(container);
        accessibleTextureView.embeddedView = embeddedView;
        embeddedView.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            //For Amir: You can add a delegate like this to every layout in the hierarchy using ViewTreeObserver.
            //    Make sure you check to see if a delegate is already on the view, if so have yoru delegate take a reference to it, and call that delegate instead of super.
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setParent(accessibleTextureView);
                Rect textureBounds = new Rect();
                accessibleTextureView.createAccessibilityNodeInfo().getBoundsInScreen(textureBounds);
                Rect bounds = new Rect();
                info.getBoundsInScreen(bounds);
                bounds.offsetTo(textureBounds.left, textureBounds.top);
                info.setBoundsInScreen(bounds);
            }

        });
        AccessibilityManager accessibilityManager =
                (AccessibilityManager) getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (accessibilityManager.isEnabled()) {
            //TOOD(amir): what if accessibility is turned on after onCreate ?
            accessibleTextureView.sendContentChanged();
        }
    }
}

class AccessibilityDelegatingFrameLayout extends FrameLayout {

    View accessibilityDelegate;

    public AccessibilityDelegatingFrameLayout(@NonNull Context context, View accessibilityDelegate) {
        super(context);
        this.accessibilityDelegate = accessibilityDelegate;
    }

    @Override
    public boolean requestSendAccessibilityEvent(View child, AccessibilityEvent event) {
        return accessibilityDelegate.getParent().requestSendAccessibilityEvent(child, event);
    }
}

class AccessibleTextureView extends TextureView {

    View embeddedView;

    public AccessibleTextureView(Context context) {
        super(context);
    }

    public void sendContentChanged() {
        AccessibilityEvent event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
        event.setPackageName(getContext().getPackageName());
        //event.setSource(this, AccessibilityNodeProvider.HOST_VIEW_ID);
        getParent().requestSendAccessibilityEvent(this, event);
    }

    final <T extends View> T findViewByAccessibilityIdTraversal(int accessibilityId) {
        View result = findViewByAccessibilityIdTraversalInNestedHierarchy(this, accessibilityId);
        if (result == null) {
            result = findViewByAccessibilityIdTraversalInNestedHierarchy(embeddedView, accessibilityId);
        }

        return (T)result;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.addChild(embeddedView);
    }

    private int getAccessibilityIdForView(View view) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return getAccessbilityIdForViewPreAndroidP(view);
        }
        return getAccessibilityIdForViewAndroidP(view);
    }

    //Big hack, but you can extract the accessibility ID from the hash.
    private int getAccessibilityIdForViewAndroidP(View view) {
        AccessibilityNodeInfo nodeInfo = view.createAccessibilityNodeInfo();
        final int prime = 31;
        final int hostId = -1;
        int id = nodeInfo.hashCode();
        id -= nodeInfo.getWindowId();
        id /= prime;
        id -= hostId;
        id /= prime;
        id %= prime;
        return id;
    }

    private int getAccessbilityIdForViewPreAndroidP(View view) {
        try {
            Class clazz = Class.forName("android.view.View");
            Method method = clazz.getMethod("getAccessibilityViewId");
            return (int) method.invoke(view);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("expecting to find the android.view.View class");
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e.getMessage());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e.getMessage());
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(e.getMessage());
        }
    }

    //This part shouldn't be needed in Q+
    private View findViewByAccessibilityIdTraversalInNestedHierarchy(View view, int accessibilityId) {
        if (accessibilityId == getAccessibilityIdForView(view)) {
            return view;
        } else if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View result = findViewByAccessibilityIdTraversalInNestedHierarchy(vg.getChildAt(i), accessibilityId);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    @Override
    public boolean isImportantForAccessibility() {
        return true;
    }
}
