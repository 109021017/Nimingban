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

package com.hippo.nimingban.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.hippo.nimingban.R;
import com.hippo.nimingban.widget.DirExplorer;
import com.hippo.rippleold.RippleSalon;
import com.hippo.styleable.StyleableActivity;

import java.io.File;

public class DirPickerActivity extends StyleableActivity implements View.OnClickListener, DirExplorer.OnChangeDirListener {

    public static final String KEY_FILE_URI = "file_uri";

    private TextView mPath;
    private DirExplorer mDirExplorer;
    private View mOk;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_dir_picker);

        mPath = (TextView) findViewById(R.id.path);
        mDirExplorer = (DirExplorer) findViewById(R.id.dir_explorer);
        mOk = findViewById(R.id.ok);

        File file = null;
        Intent intent = getIntent();
        if (intent != null) {
            Uri fileUri = intent.getParcelableExtra(KEY_FILE_URI);
            if (fileUri != null) {
                file = new File(fileUri.getPath());
            }
        }
        mDirExplorer.setCurrentFile(file);
        mDirExplorer.setOnChangeDirListener(this);

        RippleSalon.addRipple(mOk, false); // TODO darktheme

        mOk.setOnClickListener(this);

        mPath.setText(mDirExplorer.getCurrentFile().getPath());
    }

    @Override
    public void onClick(View v) {
        if (mOk == v) {
            File file = mDirExplorer.getCurrentFile();
            if (!file.canWrite()) {
                Toast.makeText(this, R.string.directory_not_writable, Toast.LENGTH_SHORT).show();
            } else {
                Intent intent = new Intent();
                intent.setData(Uri.fromFile(file));
                setResult(RESULT_OK, intent);
                finish();
            }
        }
    }

    @Override
    public void onChangeDir(File dir) {
        mPath.setText(dir.getPath());
    }
}
