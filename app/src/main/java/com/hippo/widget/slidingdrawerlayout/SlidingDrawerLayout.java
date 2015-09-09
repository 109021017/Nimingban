/*
 * Copyright 2015 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.widget.slidingdrawerlayout;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.ViewDragHelper;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;

import com.hippo.util.AnimationUtils;
import com.hippo.yorozuya.MathUtils;
import com.hippo.yorozuya.ViewUtils;

@SuppressLint("RtlHardcoded")
public class SlidingDrawerLayout extends ViewGroup implements ValueAnimator.AnimatorUpdateListener,
        Animator.AnimatorListener {

    private static final int[] LAYOUT_ATTRS = new int[] {
        android.R.attr.layout_gravity
    };

    private static final long ANIMATE_TIME = 300;

    private static final int STATE_CLOSED = 0;
    private static final int STATE_SLIDING = 1;
    private static final int STATE_OPEN = 2;

    /**
     * The drawer is unlocked.
     */
    public static final int LOCK_MODE_UNLOCKED = 0;

    /**
     * The drawer is locked closed. The user may not open it, though the app may
     * open it programmatically.
     */
    public static final int LOCK_MODE_LOCKED_CLOSED = 1;

    /**
     * The drawer is locked open. The user may not close it, though the app may
     * close it programmatically.
     */
    public static final int LOCK_MODE_LOCKED_OPEN = 2;

    private static final float OPEN_SENSITIVITY = 0.1f;
    private static final float CLOSE_SENSITIVITY = 0.9f;

    private int mMinDrawerMargin;
    private boolean mInLayout;

    private View mContentView;
    private View mLeftDrawer;
    private View mRightDrawer;
    private ShadowView mShadow;

    private ViewDragHelper mDragHelper;
    private float mInitialMotionX;
    private float mInitialMotionY;

    private boolean mLeftOpened;
    private boolean mRightOpened;
    private int mLeftState;
    private int mRightState;
    private float mLeftPercent;
    private float mRightPercent;
    private int mLeftLockMode;
    private int mRightLockMode;

    private ValueAnimator mAnimator;
    private View mTargetView;
    private int mStartLeft;
    private int mEndLeft;
    private boolean mToOpen;
    private boolean mCancelAnimation;
    private View mOpenTask;

    private DrawerListener mListener;

    private Drawable mStatusBarBackground;

    private boolean mIntercepted;
    private int mInterceptPointNum;

    private Object mLastInsets;
    private boolean mDrawStatusBarBackground;

    private static int sDefaultMinDrawerMargin = 56;

    private static final SlidingDrawerLayoutCompatImpl IMPL;

    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            IMPL = new SlidingDrawerLayoutCompatImplApi21();
        } else {
            IMPL = new SlidingDrawerLayoutCompatImplBase();
        }
    }

    interface SlidingDrawerLayoutCompatImpl {
        void configureApplyInsets(View drawerLayout);
        void dispatchChildInsets(View child, Object insets, int drawerGravity);
        void applyMarginInsets(MarginLayoutParams lp, Object insets, int drawerGravity);
        int getTopInset(Object lastInsets);
    }

    static class SlidingDrawerLayoutCompatImplBase implements SlidingDrawerLayoutCompatImpl {
        @Override
        public void configureApplyInsets(View drawerLayout) {
            // This space for rent
        }

        @Override
        public void dispatchChildInsets(View child, Object insets, int drawerGravity) {
            // This space for rent
        }

        @Override
        public void applyMarginInsets(MarginLayoutParams lp, Object insets, int drawerGravity) {
            // This space for rent
        }

        @Override
        public int getTopInset(Object insets) {
            return 0;
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    static class SlidingDrawerLayoutCompatImplApi21 implements SlidingDrawerLayoutCompatImpl {
        @Override
        public void configureApplyInsets(View drawerLayout) {
            if (drawerLayout instanceof SlidingDrawerLayout) {
                drawerLayout.setOnApplyWindowInsetsListener(new InsetsListener());
                drawerLayout.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
            }
        }

        @Override
        public void dispatchChildInsets(View child, Object insets, int gravity) {
            WindowInsets wi = (WindowInsets) insets;
            if (gravity == Gravity.LEFT) {
                wi = wi.replaceSystemWindowInsets(wi.getSystemWindowInsetLeft(),
                        wi.getSystemWindowInsetTop(), 0, wi.getSystemWindowInsetBottom());
            } else if (gravity == Gravity.RIGHT) {
                wi = wi.replaceSystemWindowInsets(0, wi.getSystemWindowInsetTop(),
                        wi.getSystemWindowInsetRight(), wi.getSystemWindowInsetBottom());
            }
            child.dispatchApplyWindowInsets(wi);
        }

        @Override
        public void applyMarginInsets(MarginLayoutParams lp, Object insets, int gravity) {
            WindowInsets wi = (WindowInsets) insets;
            if (gravity == Gravity.LEFT) {
                wi = wi.replaceSystemWindowInsets(wi.getSystemWindowInsetLeft(),
                        wi.getSystemWindowInsetTop(), 0, wi.getSystemWindowInsetBottom());
            } else if (gravity == Gravity.RIGHT) {
                wi = wi.replaceSystemWindowInsets(0, wi.getSystemWindowInsetTop(),
                        wi.getSystemWindowInsetRight(), wi.getSystemWindowInsetBottom());
            }
            lp.leftMargin = wi.getSystemWindowInsetLeft();
            lp.topMargin = wi.getSystemWindowInsetTop();
            lp.rightMargin = wi.getSystemWindowInsetRight();
            lp.bottomMargin = wi.getSystemWindowInsetBottom();
        }

        @Override
        public int getTopInset(Object insets) {
            return insets != null ? ((WindowInsets) insets).getSystemWindowInsetTop() : 0;
        }

        static class InsetsListener implements OnApplyWindowInsetsListener {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                final SlidingDrawerLayout drawerLayout = (SlidingDrawerLayout) v;
                drawerLayout.setChildInsets(insets, insets.getSystemWindowInsetTop() > 0);
                return insets.consumeSystemWindowInsets();
            }
        }
    }

    /**
     * Listener for monitoring events about drawers.
     */
    public interface DrawerListener {
        /**
         * Called when a drawer's position changes.
         * @param drawerView The child view that was moved
         * @param percent The new offset of this drawer within its range, from 0-1
         */
        void onDrawerSlide(View drawerView, float percent);

        /**
         * Called when a drawer has settled in a completely open state.
         * The drawer is interactive at this point.
         *
         * @param drawerView Drawer view that is now open
         */
        void onDrawerOpened(View drawerView);

        /**
         * Called when a drawer has settled in a completely closed state.
         *
         * @param drawerView Drawer view that is now closed
         */
        void onDrawerClosed(View drawerView);

        /**
         * Called when the drawer motion state changes. The new state will
         * be one of {@link #STATE_CLOSED}, {@link #STATE_SLIDING} or {@link #STATE_OPEN}.
         *
         * @param newState The new drawer motion state
         */
        void onDrawerStateChanged(View drawerView, int newState);
    }

    /**
     * minMargin should be app bar height in Material design
     *
     * @param minMargin
     */
    public static void setDefaultMinDrawerMargin(int minMargin) {
        sDefaultMinDrawerMargin = minMargin;
    }

    public SlidingDrawerLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SlidingDrawerLayout(Context context, AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public SlidingDrawerLayout(Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        mLeftOpened = false;
        mRightOpened = false;
        mLeftState = STATE_CLOSED;
        mRightState = STATE_CLOSED;
        mLeftPercent = 0.0f;
        mRightPercent = 0.0f;
        mMinDrawerMargin = sDefaultMinDrawerMargin;
        mDragHelper = ViewDragHelper.create(this, 0.5f, new DragHelperCallback());
        mAnimator = ValueAnimator.ofFloat(0.0f, 1.0f);
        mAnimator.addUpdateListener(this);
        mAnimator.addListener(this);
        mAnimator.setInterpolator(AnimationUtils.FAST_SLOW_INTERPOLATOR);
        mCancelAnimation = false;

        setWillNotDraw(false);
    }

    public void setDrawerListener(DrawerListener listener) {
        mListener = listener;
    }

    /**
     * Close all currently open drawer views by animating them out of view.
     */
    public void closeDrawers() {
        closeDrawer(mLeftDrawer);
        closeDrawer(mRightDrawer);
    }

    /**
     * Open the specified drawer view by animating it into view.
     *
     * @param drawerView
     *            Drawer view to open
     */
    public void openDrawer(View drawerView) {
        if (drawerView == null || (drawerView != mLeftDrawer && drawerView != mRightDrawer))
            return;

        boolean isLeft = drawerView == mLeftDrawer;
        switch (isLeft ? mLeftState : mRightState) {
        case STATE_CLOSED:
            // Check other side first
            switch (isLeft ? mRightState : mLeftState) {
            case STATE_CLOSED:
                startAnimation(isLeft, true, false);
                break;
            case STATE_SLIDING:
                if (mAnimator.isRunning()) {
                    if (mToOpen) {
                        cancelAnimation();
                        mOpenTask = isLeft ? mLeftDrawer : mRightDrawer;
                        startAnimation(!isLeft, false, false);
                    } else {
                        mOpenTask = isLeft ? mLeftDrawer : mRightDrawer;
                    }
                } else {
                    // You finger is on Screen!
                }
                break;
            case STATE_OPEN:
                // Close other side first
                mOpenTask = isLeft ? mLeftDrawer : mRightDrawer;
                startAnimation(!isLeft, false, false);
                break;
            }
            break;

        case STATE_SLIDING:
            if (mAnimator.isRunning()) {
                if (mToOpen) {
                    // Same purpose
                } else {
                    cancelAnimation();
                    startAnimation(isLeft, true, false);
                }
            } else {
                // You finger is on Screen!
            }
            break;

        case STATE_OPEN:
            // Ok it is opened
            break;
        }
    }

    public void openDrawer(int gravity) {
        if (gravity == Gravity.LEFT)
            openDrawer(mLeftDrawer);
        else if (gravity == Gravity.RIGHT)
            openDrawer(mRightDrawer);
    }

    /**
     * Close the specified drawer view by animating it into view.
     *
     * @param drawerView
     *            Drawer view to close
     */
    public void closeDrawer(View drawerView) {
        if (drawerView == null || (drawerView != mLeftDrawer && drawerView != mRightDrawer))
            return;

        boolean isLeft = drawerView == mLeftDrawer;
        switch (isLeft ? mLeftState : mRightState) {
        case STATE_CLOSED:
            // Ok it is closed
            break;
        case STATE_SLIDING:
            if (mAnimator.isRunning()) {
                if (mToOpen) {
                    cancelAnimation();
                    startAnimation(isLeft, false, false);
                } else {
                    // Same purpose
                }
            } else {
                // You finger is on Screen!
            }
            break;
        case STATE_OPEN:
            startAnimation(isLeft, false, false);
            break;
        }
    }

    public void closeDrawer(int gravity) {
        if (gravity == Gravity.LEFT)
            closeDrawer(mLeftDrawer);
        else if (gravity == Gravity.RIGHT)
            closeDrawer(mRightDrawer);
    }

    public boolean isDrawerOpen(View drawer) {
        if (drawer == mLeftDrawer)
            return mLeftOpened;
        else if (drawer == mRightDrawer)
            return mRightOpened;
        else
            throw new IllegalArgumentException("The view is not drawer");
    }

    public boolean isDrawerOpen(int gravity) {
        if (gravity == Gravity.LEFT)
            return isDrawerOpen(mLeftDrawer);
        else if (gravity == Gravity.RIGHT)
            return isDrawerOpen(mRightDrawer);
        else
            throw new IllegalArgumentException("gravity must be Gravity.LEFT or Gravity.RIGHT");
    }

    public void setDrawerShadow(Drawable shadowDrawable, int gravity) {
        switch (gravity) {
            case Gravity.LEFT:
                mShadow.setShadowLeft(shadowDrawable);
                break;
            case Gravity.RIGHT:
                mShadow.setShadowRight(shadowDrawable);
                break;
            default:
                throw new IllegalStateException("setDrawerShadow only support Gravity.LEFT and Gravity.RIGHT");
        }
        invalidate();
    }

    public void setDrawerLockMode(int lockMode, int gravity) {
        if (gravity == Gravity.LEFT)
            setDrawerLockMode(gravity, mLeftDrawer);
        else if (gravity == Gravity.RIGHT)
            setDrawerLockMode(gravity, mRightDrawer);
        else
            throw new IllegalArgumentException("gravity must be Gravity.LEFT or Gravity.RIGHT");
    }

    public void setDrawerLockMode(int lockMode, View drawer) {
        if (drawer != mLeftDrawer && drawer != mRightDrawer)
            throw new IllegalArgumentException("The view is not drawer");

        int oldLockMode;
        int otherSideLockMode;
        if (drawer == mLeftDrawer) {
            oldLockMode = mLeftLockMode;
            otherSideLockMode = mRightLockMode;
            mLeftLockMode = lockMode;
        } else {
            oldLockMode = mRightLockMode;
            otherSideLockMode = mLeftLockMode;
            mRightLockMode = lockMode;
        }
        if (oldLockMode == lockMode)
            return;

        if (otherSideLockMode == LOCK_MODE_LOCKED_OPEN && lockMode == LOCK_MODE_LOCKED_OPEN)
            throw new IllegalArgumentException("Only on side could be LOCK_MODE_LOCKED_OPEN");

        switch (lockMode) {
        // TODO What if open or close fail ?
        case LOCK_MODE_LOCKED_OPEN:
            openDrawer(drawer);
            break;
        case LOCK_MODE_LOCKED_CLOSED:
            closeDrawer(drawer);
            break;
        }
    }

    public int getDrawerLockMode(int gravity) {
        if (gravity == Gravity.LEFT)
            return getDrawerLockMode(mLeftDrawer);
        else if (gravity == Gravity.RIGHT)
            return getDrawerLockMode(mRightDrawer);
        else
            throw new IllegalArgumentException("gravity must be Gravity.LEFT or Gravity.RIGHT");
    }

    public int getDrawerLockMode(View drawer) {
        if (drawer == mLeftDrawer)
            return mLeftLockMode;
        else if (drawer == mRightDrawer)
            return mRightLockMode;
        else
            throw new IllegalArgumentException("The view is not drawer");
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            final int absGravity = GravityCompat.getAbsoluteGravity(
                    ((LayoutParams) child.getLayoutParams()).gravity,
                    ViewCompat.getLayoutDirection(child));

            if (absGravity == Gravity.NO_GRAVITY) {
                if (mContentView != null)
                    throw new IllegalStateException("There is more than one content view");
                mContentView = child;
            } else if ((absGravity & Gravity.LEFT) == Gravity.LEFT) {
                if (mLeftDrawer != null)
                    throw new IllegalStateException("There is more than one left menu");
                mLeftDrawer = child;
            } else if ((absGravity & Gravity.RIGHT) == Gravity.RIGHT) {
                if (mRightDrawer != null)
                    throw new IllegalStateException("There is more than one right menu");
                mRightDrawer = child;
            } else {
                throw new IllegalStateException("Child " + child + " at index " + i +
                        " does not have a valid layout_gravity - must be Gravity.LEFT, " +
                        "Gravity.RIGHT or Gravity.NO_GRAVITY");
            }
        }

        if (mContentView == null)
            throw new IllegalStateException("There is no content view");
        // Material is solid.
        // Input events cannot pass through material.
        if (mLeftDrawer != null)
            mLeftDrawer.setClickable(true);
        if (mRightDrawer != null)
            mRightDrawer.setClickable(true);

        mShadow = new ShadowView(getContext());
        addView(mShadow, 1);
    }

    /**
     * @hide Internal use only; called to apply window insets when configured
     * with fitsSystemWindows="true"
     */
    public void setChildInsets(Object insets, boolean draw) {
        mLastInsets = insets;
        mDrawStatusBarBackground = draw;
        setWillNotDraw(!draw && getBackground() == null);
        requestLayout();
    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        if (widthMode != MeasureSpec.EXACTLY || heightMode != MeasureSpec.EXACTLY)
            throw new IllegalArgumentException(
                    "SlidingDrawerLayout must be measured with MeasureSpec.EXACTLY.");

        setMeasuredDimension(widthSize, heightSize);

        final boolean applyInsets = mLastInsets != null && ViewCompat.getFitsSystemWindows(this);
        final int layoutDirection = ViewCompat.getLayoutDirection(this);

        // Gravity value for each drawer we've seen. Only one of each permitted.
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);

            if (child.getVisibility() == GONE) {
                continue;
            }

            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
            if (applyInsets) {
                final int cgrav = GravityCompat.getAbsoluteGravity(lp.gravity, layoutDirection);
                if (ViewCompat.getFitsSystemWindows(child)) {
                    IMPL.dispatchChildInsets(child, mLastInsets, cgrav);
                } else {
                    IMPL.applyMarginInsets(lp, mLastInsets, cgrav);
                }
            }

            if (child == mContentView) {
                // Content views get measured at exactly the layout's size.
                final int contentWidthSpec = MeasureSpec.makeMeasureSpec(
                        widthSize - lp.leftMargin - lp.rightMargin, MeasureSpec.EXACTLY);
                final int contentHeightSpec = MeasureSpec.makeMeasureSpec(
                        heightSize - lp.topMargin - lp.bottomMargin, MeasureSpec.EXACTLY);
                child.measure(contentWidthSpec, contentHeightSpec);
            } else if (child == mLeftDrawer || child == mRightDrawer) {
                final int drawerWidthSpec = getChildMeasureSpec(widthMeasureSpec,
                        lp.leftMargin + lp.rightMargin,
                        Math.min(widthSize - mMinDrawerMargin, lp.width));
                final int drawerHeightSpec = getChildMeasureSpec(heightMeasureSpec,
                        lp.topMargin + lp.bottomMargin,
                        lp.height);
                child.measure(drawerWidthSpec, drawerHeightSpec);
            } else if (child == mShadow) {
                child.measure(widthMeasureSpec, heightMeasureSpec);
            } else {
                throw new IllegalStateException("Don't call addView");
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        mInLayout = true;
        final int width = r - l;
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);

            if (child.getVisibility() == GONE) {
                continue;
            }

            final LayoutParams lp = (LayoutParams) child.getLayoutParams();

            if (child == mContentView) {
                child.layout(lp.leftMargin, lp.topMargin,
                        lp.leftMargin + child.getMeasuredWidth(),
                        lp.topMargin + child.getMeasuredHeight());
            } else if (child == mShadow) {
                child.layout(l, t, r, b);
            } else { // Drawer, if it wasn't onMeasure would have thrown an exception.
                final int childWidth = child.getMeasuredWidth();
                final int childHeight = child.getMeasuredHeight();
                int childLeft;
                float percent;

                if (child == mLeftDrawer) {
                    percent = mLeftPercent;
                    childLeft = -childWidth + (int) (childWidth * percent);
                } else { // Right; onMeasure checked for us.
                    percent = mRightPercent;
                    childLeft = width - (int) (childWidth * percent);
                }

                final int vgrav = lp.gravity & Gravity.VERTICAL_GRAVITY_MASK;

                switch (vgrav) {
                    default:
                    case Gravity.TOP: {
                        child.layout(childLeft, lp.topMargin, childLeft + childWidth,
                                lp.topMargin + childHeight);
                        break;
                    }

                    case Gravity.BOTTOM: {
                        final int height = b - t;
                        child.layout(childLeft,
                                height - lp.bottomMargin - child.getMeasuredHeight(),
                                childLeft + childWidth,
                                height - lp.bottomMargin);
                        break;
                    }

                    case Gravity.CENTER_VERTICAL: {
                        final int height = b - t;
                        int childTop = (height - childHeight) / 2;

                        // Offset for margins. If things don't fit right because of
                        // bad measurement before, oh well.
                        if (childTop < lp.topMargin) {
                            childTop = lp.topMargin;
                        } else if (childTop + childHeight > height - lp.bottomMargin) {
                            childTop = height - lp.bottomMargin - childHeight;
                        }
                        child.layout(childLeft, childTop, childLeft + childWidth,
                                childTop + childHeight);
                        break;
                    }
                }

                final int newVisibility = percent > 0 ? VISIBLE : INVISIBLE;
                if (child.getVisibility() != newVisibility) {
                    child.setVisibility(newVisibility);
                }
            }
        }
        mInLayout = false;
    }

    @Override
    public void requestLayout() {
        if (!mInLayout) {
            super.requestLayout();
        }
    }

    private boolean isDrawerUnder(int x, int y) {
        return ViewUtils.isViewUnder(mLeftDrawer, x, y, 0) || ViewUtils.isViewUnder(mRightDrawer, x, y, 0);
    }

    private boolean isDrawersTouchable() {
        if ((mLeftDrawer != null && mLeftLockMode == LOCK_MODE_UNLOCKED)
                || (mRightDrawer != null && mRightLockMode == LOCK_MODE_UNLOCKED))
            return true;
        else
            return false;
    }

    private int getActualDxLeft(int dx) {
        int leftWidth = mLeftDrawer.getWidth();
        int oldLeft = mLeftDrawer.getLeft();
        int newLeft = MathUtils.clamp(oldLeft + dx, -leftWidth, 0);
        int newVisible = newLeft == -leftWidth ? View.INVISIBLE : View.VISIBLE;
        if (mLeftDrawer.getVisibility() != newVisible)
            mLeftDrawer.setVisibility(newVisible);

        updateDrawerSlide(mLeftDrawer, (newLeft + leftWidth) / (float) leftWidth);

        return newLeft - oldLeft;
    }

    private int getActualDxRight(int dx) {
        int width = getWidth();
        int rightWidth = mRightDrawer.getWidth();
        int oldLeft = mRightDrawer.getLeft();
        int newLeft = MathUtils.clamp(oldLeft + dx, width - rightWidth, width);
        int newVisible = newLeft == width ? View.INVISIBLE : View.VISIBLE;
        if (mRightDrawer.getVisibility() != newVisible)
            mRightDrawer.setVisibility(newVisible);

        updateDrawerSlide(mRightDrawer, (width - newLeft) / (float) rightWidth);

        return newLeft - oldLeft;
    }

    private void slideLeftDrawer(int dx) {
        mLeftDrawer.offsetLeftAndRight(getActualDxLeft(dx));
    }

    private void slideRightDrawer(int dx) {
        mRightDrawer.offsetLeftAndRight(getActualDxRight(dx));
    }

    @Override
    public void onAnimationUpdate(ValueAnimator animation) {
        float value = (Float) animation.getAnimatedValue();
        int oldLeft = mTargetView.getLeft();
        int newLeft = MathUtils.lerp(mStartLeft, mEndLeft, value);
        if (mTargetView == mLeftDrawer)
            slideLeftDrawer(newLeft - oldLeft);
        else
            slideRightDrawer(newLeft - oldLeft);
    }

    @Override
    public void onAnimationStart(Animator animation) {
        updateDrawerState(mTargetView, STATE_SLIDING);
    }

    @Override
    public void onAnimationEnd(Animator animation) {
        if (!mCancelAnimation) {
            updateDrawerSlide(mTargetView, mToOpen ? 1.0f : 0.0f);
            updateDrawerState(mTargetView, mToOpen ? STATE_OPEN : STATE_CLOSED);
            if (mToOpen)
                dispatchOnDrawerOpened(mTargetView);
            else
                dispatchOnDrawerClosed(mTargetView);

            if (mOpenTask != null && !mToOpen &&
                    (mOpenTask == mLeftDrawer || mOpenTask == mRightDrawer)) {
                // If call startAnimation directly,
                // onAnimationStart and onAnimationEnd will not be called
                final boolean isLeft = mOpenTask == mLeftDrawer;
                getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        startAnimation(isLeft, true, false);
                    }
                });
            }
        }

        mCancelAnimation = false;
        mOpenTask = null;
    }

    @Override
    public void onAnimationCancel(Animator animation) {
        // Empty
    }

    @Override
    public void onAnimationRepeat(Animator animation) {
        // Empty
    }

    private void startAnimation(final boolean isLeft, final boolean isOpen,
            boolean staticDuration) {
        mToOpen = isOpen;
        int total;
        if (isLeft) {
            mTargetView = mLeftDrawer;
            mStartLeft = mLeftDrawer.getLeft();
            total = mLeftDrawer.getWidth();
            mEndLeft = mToOpen ? 0 : -total;
        } else {
            mTargetView = mRightDrawer;
            mStartLeft = mRightDrawer.getLeft();
            total = mRightDrawer.getWidth();
            mEndLeft = mToOpen ? getWidth() - total : getWidth();
        }

        if (mStartLeft == mEndLeft) {
            // No need to animate
            updateDrawerSlide(mTargetView, mToOpen ? 1.0f : 0.0f);
            updateDrawerState(mTargetView, mToOpen ? STATE_OPEN : STATE_CLOSED);
            if (mToOpen)
                dispatchOnDrawerOpened(mTargetView);
            else
                dispatchOnDrawerClosed(mTargetView);
            return;
        }

        mAnimator.setDuration(staticDuration ? ANIMATE_TIME : ANIMATE_TIME * Math.abs(mStartLeft - mEndLeft) / total);

        mAnimator.start();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {

        final int action = MotionEventCompat.getActionMasked(ev);
        final float x = ev.getX();
        final float y = ev.getY();
        boolean interceptSlide = false;

        if (!isDrawersTouchable()) {
            return false;
        }

        switch (action) {
        case MotionEvent.ACTION_DOWN:
            mIntercepted = false;
            mInterceptPointNum = 0;
            mInitialMotionX = x;
            mInitialMotionY = y;
            break;
        case MotionEvent.ACTION_MOVE:
            final float adx = Math.abs(x - mInitialMotionX);
            final float ady = Math.abs(y - mInitialMotionY);
            final int slop = mDragHelper.getTouchSlop();
            if (adx > slop && adx > ady)
                interceptSlide = true;
            break;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
            mDragHelper.cancel();
            break;
        }

        if (++mInterceptPointNum > 8)
            return false;

        mIntercepted = mDragHelper.shouldInterceptTouchEvent(ev) || interceptSlide;
        return mIntercepted;
    }

    private void cancelAnimation() {
        if (mAnimator.isRunning()) {
            mOpenTask = null;
            mCancelAnimation = true;
            mAnimator.cancel();
        }
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    public boolean onTouchEvent(MotionEvent ev) {

        // Cancel animate
        cancelAnimation();

        try {
            mDragHelper.processTouchEvent(ev);
        } catch (Throwable e) {
            // TODO Another finger touch screen may make trouble
        }

        final int action = MotionEventCompat.getActionMasked(ev);
        final float x = ev.getX();
        final float y = ev.getY();

        if (action == MotionEvent.ACTION_DOWN) {
            mInterceptPointNum = 1;
            mIntercepted = true;
        }

        if (!mIntercepted)
            return false;

        if (!isDrawersTouchable())
            return false;

        switch (action) {
        case MotionEvent.ACTION_DOWN:
            mInitialMotionX = x;
            mInitialMotionY = y;
            break;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
            mInterceptPointNum = 0;

            if (mLeftState == STATE_SLIDING) {
                if (mLeftOpened && mLeftPercent < CLOSE_SENSITIVITY)
                    startAnimation(true, false, false);
                else if (!mLeftOpened && mLeftPercent < OPEN_SENSITIVITY)
                    startAnimation(true, false, true);
                else if (mLeftOpened && mLeftPercent >= CLOSE_SENSITIVITY)
                    startAnimation(true, true, true);
                else if (!mLeftOpened && mLeftPercent >= OPEN_SENSITIVITY)
                    startAnimation(true, true, false);
            } else if (mRightState == STATE_SLIDING) {
                if (mRightOpened && mRightPercent < CLOSE_SENSITIVITY)
                    startAnimation(false, false, false);
                else if (!mRightOpened && mRightPercent < OPEN_SENSITIVITY)
                    startAnimation(false, false, true);
                else if (mRightOpened && mRightPercent >= CLOSE_SENSITIVITY)
                    startAnimation(false, true, true);
                else if (!mRightOpened && mRightPercent >= OPEN_SENSITIVITY)
                    startAnimation(false, true, false);
            }
            break;
        }

        return true;
    }

    @Override
    protected int getChildDrawingOrder(int childCount, int i) {
        return i;
    }

    /**
     * Set a drawable to draw in the insets area for the status bar.
     * Note that this will only be activated if this DrawerLayout fitsSystemWindows.
     *
     * @param bg Background drawable to draw behind the status bar
     */
    public void setStatusBarBackground(Drawable bg) {
        mStatusBarBackground = bg;
    }

    /**
     * Set a drawable to draw in the insets area for the status bar.
     * Note that this will only be activated if this DrawerLayout fitsSystemWindows.
     *
     * @param resId Resource id of a background drawable to draw behind the status bar
     */
    public void setStatusBarBackground(int resId) {
        mStatusBarBackground = resId != 0 ? ContextCompat.getDrawable(getContext(), resId) : null;
    }

    /**
     * Set a drawable to draw in the insets area for the status bar.
     * Note that this will only be activated if this DrawerLayout fitsSystemWindows.
     *
     * @param color Color to use as a background drawable to draw behind the status bar
     *              in 0xAARRGGBB format.
     */
    public void setStatusBarBackgroundColor(int color) {
        mStatusBarBackground = new ColorDrawable(color);
    }

    @Override
    public void onDraw(Canvas c) {
        super.onDraw(c);
        if (mDrawStatusBarBackground && mStatusBarBackground != null) {
            final int inset = IMPL.getTopInset(mLastInsets);
            if (inset > 0) {
                mStatusBarBackground.setBounds(0, 0, getWidth(), inset);
                mStatusBarBackground.draw(c);
            }
        }
    }

    private void updateDrawerSlide(View drawerView, float percent) {
        boolean update = false;
        // Update percent
        if (drawerView == mLeftDrawer) {
            update = mLeftPercent != percent;
            mLeftPercent = percent;
        } else if (drawerView == mRightDrawer) {
            update = mRightPercent != percent;
            mRightPercent = percent;
        }

        if (update)
            mShadow.setPercent(percent);

        // Callback
        if (update && mListener != null) {
            mListener.onDrawerSlide(drawerView, percent);
        }
    }

    private void updateDrawerState(View drawerView, int state) {
        boolean update = false;
        // Update state
        if (drawerView == mLeftDrawer) {
            update = mLeftState != state;
            mLeftState = state;
        } else if (drawerView == mRightDrawer) {
            update = mRightState != state;
            mRightState = state;
        }

        // Callback
        if (update && mListener != null) {
            mListener.onDrawerStateChanged(drawerView, state);
        }
    }

    private void dispatchOnDrawerClosed(View drawerView) {
        boolean update = false;
        // Update
        if (drawerView == mLeftDrawer) {
            update = mLeftOpened;
            mLeftOpened = false;
        } else if (drawerView == mRightDrawer) {
            update = mRightOpened;
            mRightOpened = false;
        }

        // Callback
        if (update && mListener != null) {
            mListener.onDrawerClosed(drawerView);
        }
    }

    private void dispatchOnDrawerOpened(View drawerView) {
        boolean update = false;
        // Update
        if (drawerView == mLeftDrawer) {
            update = !mLeftOpened;
            mLeftOpened = true;
        } else if (drawerView == mRightDrawer) {
            update = !mRightOpened;
            mRightOpened = true;
        }

        // Callback
        if (update && mListener != null) {
            mListener.onDrawerOpened(drawerView);
        }
    }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams
                ? new LayoutParams((LayoutParams) p)
                : p instanceof MarginLayoutParams
                ? new LayoutParams((MarginLayoutParams) p)
                : new LayoutParams(p);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams && super.checkLayoutParams(p);
    }

    @Override
    public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    public static class LayoutParams extends MarginLayoutParams {

        public int gravity = Gravity.NO_GRAVITY;

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);

            final TypedArray a = c.obtainStyledAttributes(attrs, LAYOUT_ATTRS);
            this.gravity = a.getInt(0, Gravity.NO_GRAVITY);
            a.recycle();
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(int width, int height, int gravity) {
            this(width, height);
            this.gravity = gravity;
        }

        public LayoutParams(LayoutParams source) {
            super(source);
            this.gravity = source.gravity;
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }

        public LayoutParams(MarginLayoutParams source) {
            super(source);
        }
    }

    private class DragHelperCallback extends ViewDragHelper.Callback {

        @Override
        public boolean tryCaptureView(View child, int pointerId) {
            return true;
        }

        @Override
        public int clampViewPositionVertical(View child, int top, int dy) {
            return child.getTop();
        }

        @Override
        public int clampViewPositionHorizontal(View child, int left, int dx) {

            if (!mIntercepted)
                return child.getLeft();

            if (child == mContentView || child == mShadow) {
                if (mLeftState == STATE_CLOSED && mRightState == STATE_CLOSED) {
                    if (dx > 0 && mLeftDrawer != null && mLeftLockMode == LOCK_MODE_UNLOCKED) {
                        slideLeftDrawer(dx);
                        updateDrawerState(mLeftDrawer, STATE_SLIDING);
                    } else if (dx < 0 && mRightDrawer != null && mRightLockMode == LOCK_MODE_UNLOCKED) {
                        slideRightDrawer(dx);
                        updateDrawerState(mRightDrawer, STATE_SLIDING);
                    }
                } else if (mLeftState != STATE_CLOSED) {
                    slideLeftDrawer(dx);
                    updateDrawerState(mLeftDrawer, STATE_SLIDING);
                } else if (mRightState != STATE_CLOSED) {
                    slideRightDrawer(dx);
                    updateDrawerState(mRightDrawer, STATE_SLIDING);
                }
                return child.getLeft();

            } else if (child == mLeftDrawer) {
                updateDrawerState(mLeftDrawer, STATE_SLIDING);
                return child.getLeft() + getActualDxLeft(dx);

            } else if (child == mRightDrawer) {
                updateDrawerState(mRightDrawer, STATE_SLIDING);
                return child.getLeft() + getActualDxRight(dx);

            } else {
                return child.getLeft();
            }
        }

        @Override
        public int getViewHorizontalDragRange(View child) {
            return Integer.MAX_VALUE;
        }
    }

    private final class ShadowView extends View implements View.OnClickListener {

        private final int mForm = 0x0;
        private final int mTo = 0x99;
        private float mPercent = 0;

        private Drawable mShadowLeft = null;
        private Drawable mShadowRight = null;

        public ShadowView(Context context) {
            super(context);
            setSoundEffectsEnabled(false);
        }

        public void setShadowLeft(Drawable shadowLeft) {
            mShadowLeft = shadowLeft;
        }

        public void setShadowRight(Drawable shadowRight) {
            mShadowRight = shadowRight;
        }

        public void setPercent(float percent) {
            mPercent = percent;

            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            c.drawARGB(MathUtils.lerp(mForm, mTo, mPercent), 0, 0, 0);

            if (mShadowLeft != null) {
                int right = mLeftDrawer.getRight();
                if (right > 0) {
                    final int shadowWidth = mShadowLeft.getIntrinsicWidth();
                    mShadowLeft.setBounds(right, mLeftDrawer.getTop(),
                            right + shadowWidth, mLeftDrawer.getBottom());
                    mShadowLeft.setAlpha((int) (0xff * mLeftPercent));
                    mShadowLeft.draw(c);
                }
            }

            if (mShadowRight != null) {
                int left = mRightDrawer.getLeft();
                if (left < SlidingDrawerLayout.this.getWidth()) {
                    final int shadowWidth = mShadowRight.getIntrinsicWidth();
                    mShadowRight.setBounds(left - shadowWidth, mRightDrawer.getTop(),
                            left, mRightDrawer.getBottom());
                    mShadowRight.setAlpha((int) (0xff * mRightPercent));
                    mShadowRight.draw(c);
                }
            }

            if (mLeftOpened || mRightOpened) {
                setOnClickListener(this);
            } else {
                setOnClickListener(null);
                setClickable(false);
            }
        }

        @Override
        public void onClick(View v) {
            SlidingDrawerLayout.this.closeDrawers();
        }
    }
}
