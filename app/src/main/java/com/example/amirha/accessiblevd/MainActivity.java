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
import android.support.annotation.RequiresApi;
import android.view.Display;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.widget.FrameLayout;
import android.widget.TextView;

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
        container.addView(createTextView("Top text", Gravity.TOP));
        container.addView(textureView);
        container.addView(createTextView("Bottom text", Gravity.BOTTOM));
        setContentView(container);
    }

    private View createTextView(String text, int gravity) {
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        setFrameLayoutParams(textView, gravity);
        return textView;
    }

    private static void setFrameLayoutParams(View view, int gravity) {
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(400, 400);
        layoutParams.gravity = gravity;
        view.setLayoutParams(layoutParams);
    }
}

class SimplePresentation extends Presentation {

    private TextView embeddedView;
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
        embeddedView = new TextView(getContext());
        embeddedView.setText("Hello world!");
        embeddedView.setBackgroundColor(0xffff0000);
        AccessibilityDelegatingFrameLayout container = new AccessibilityDelegatingFrameLayout(getContext(), accessibleTextureView);
        container.addView(embeddedView);
        setContentView(container);
        accessibleTextureView.nodeProvider.embeddedView = container;
        accessibleTextureView.sendContentChanged();
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

    NodeProvider nodeProvider;

    public AccessibleTextureView(Context context) {
        super(context);
        nodeProvider = new NodeProvider(this);
    }


    @Override
    public AccessibilityNodeProvider getAccessibilityNodeProvider() {
        return nodeProvider;
    }

    public void sendContentChanged() {
        AccessibilityEvent event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
        event.setPackageName(getContext().getPackageName());
        event.setSource(this, AccessibilityNodeProvider.HOST_VIEW_ID);
        getParent().requestSendAccessibilityEvent(this, event);
    }

    final <T extends View> T findViewByAccessibilityIdTraversal(int accessibilityId) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        try {
            Class clazz = Class.forName("android.view.View");
            Method method = clazz.getMethod("getAccessibilityViewId");
            int aId = (int) method.invoke(this);
            if (aId == accessibilityId) {
                return (T)this;
            }

            int embeddedAccessibilityId = (int) method.invoke(nodeProvider.embeddedView);
            if (embeddedAccessibilityId == accessibilityId) {
                return (T) nodeProvider.embeddedView;
            }
        } catch (ClassNotFoundException e) {
            return null;
        }
        return null;
    }
}

class NodeProvider extends AccessibilityNodeProvider {
    View ownerView;
    View embeddedView;

    NodeProvider(View ownerView) {
        super();
        this.ownerView = ownerView;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public AccessibilityNodeInfo createAccessibilityNodeInfo(int virtualViewId) {
        if (virtualViewId == HOST_VIEW_ID) {
            AccessibilityNodeInfo node = AccessibilityNodeInfo.obtain(ownerView);
            ownerView.onInitializeAccessibilityNodeInfo(node);
            node.addChild(ownerView, 200);
            return node;
        } else if (virtualViewId == 200) {
            AccessibilityNodeInfo node = AccessibilityNodeInfo.obtain(ownerView, 200);
            node.setBoundsInParent(new Rect(0, 0, ownerView.getWidth(), ownerView.getHeight()));
            node.setBoundsInScreen(new Rect(ownerView.getLeft(), ownerView.getTop(), ownerView.getWidth() + ownerView.getLeft(), ownerView.getHeight() + ownerView.getTop()));
            if (embeddedView != null || false) {
                node.addChild(embeddedView);
            } else {
                node.setText("Yo!");
                node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS);
            }
            node.setEnabled(true);
            node.setVisibleToUser(true);
            return node;
        }
        return null;
    }

    @Override
    public boolean performAction(int virtualViewId, int action, Bundle arguments) {
        if( action == 64 && virtualViewId == 200) {
            AccessibilityEvent event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
            event.setPackageName(ownerView.getContext().getPackageName());
            event.setSource(ownerView, virtualViewId);
            ownerView.getParent().requestSendAccessibilityEvent(ownerView, event);
            return true;
        }
        return super.performAction(virtualViewId, action, arguments);
    }

}

