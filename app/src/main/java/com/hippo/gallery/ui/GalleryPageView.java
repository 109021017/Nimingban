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

package com.hippo.gallery.ui;

import android.content.Context;
import android.graphics.Rect;

import com.hippo.yorozuya.LayoutUtils;
import com.hippo.yorozuya.ResourcesUtils;

public class GalleryPageView extends FrameLayout {

    public ScaledImageView mImageView;
    public LinearLayout mLinearLayout;
    public ProgressView mProgressView;

    private Rect mSeen = new Rect();

    public GalleryPageView(Context context) {
        mImageView = new ScaledImageView();
        mLinearLayout = new LinearLayout();
        mLinearLayout.setInterval(LayoutUtils.dp2pix(context, 24)); // 12dp
        mProgressView = new ProgressView(ResourcesUtils.getAttrColor(context, android.R.attr.colorBackground));
        mProgressView.setMinimumWidth(LayoutUtils.dp2pix(context, 56)); // 56dp
        mProgressView.setMinimumHeight(LayoutUtils.dp2pix(context, 56)); // 56dp

        GravityLayoutParams lp = new GravityLayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        mLinearLayout.addComponent(mProgressView, lp);

        lp = new GravityLayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT);
        addComponent(mImageView, lp);

        lp = new GravityLayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        addComponent(mLinearLayout, lp);
    }

    private void updateSeen() {
        Rect bounds = bounds();
        Rect parentBounds = mParent.bounds();
        mSeen.set(bounds);
        if (!mSeen.intersect(0, 0, parentBounds.width(), parentBounds.height())) {
            mSeen.set(0, 0, 0, 0);
        } else {
            mSeen.offset(-bounds.left, -bounds.top);
        }
        mImageView.setSeen(mSeen);
    }

    @Override
    public void layout(int left, int top, int right, int bottom) {
        super.layout(left, top, right, bottom);
        updateSeen();
    }

    public void setAnimateState(int position) {
        mImageView.setAnimateState(position);
        mImageView.setAnimateState(position);
    }

    public boolean isLoaded() {
        return mImageView.isLoaded();
    }

    public void setScaleOffset(GalleryView.Scale scale,
            GalleryView.StartPosition startPosition, float lastScale) {
        mImageView.setScaleOffset(scale, startPosition, lastScale);
    }

    public void scroll(int dx, int dy, int[] remain) {
        mImageView.scroll(dx, dy, remain);
    }

    public void scale(float focusX, float focusY, float scale) {
        mImageView.scale(focusX, focusY, scale);
    }

    public float getFitScale() {
        return mImageView.getFitScale();
    }

    public float getScale() {
        return  mImageView.getScale();
    }
}
