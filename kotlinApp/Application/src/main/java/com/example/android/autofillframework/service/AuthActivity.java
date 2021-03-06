/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.autofillframework.service;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.assist.AssistStructure;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.service.autofill.Dataset;
import android.service.autofill.FillResponse;
import android.support.annotation.Nullable;
import android.text.Editable;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.example.android.autofillframework.R;
import com.example.android.autofillframework.service.datasource.LocalAutofillRepository;
import com.example.android.autofillframework.service.model.AutofillFieldsCollection;
import com.example.android.autofillframework.service.model.ClientFormData;
import com.example.android.autofillframework.service.settings.MyPreferences;

import java.util.HashMap;

import static android.view.autofill.AutofillManager.EXTRA_ASSIST_STRUCTURE;
import static android.view.autofill.AutofillManager.EXTRA_AUTHENTICATION_RESULT;
import static com.example.android.autofillframework.CommonUtil.EXTRA_DATASET_NAME;
import static com.example.android.autofillframework.CommonUtil.EXTRA_FOR_RESPONSE;
import static com.example.android.autofillframework.CommonUtil.TAG;

/**
 * This Activity controls the UI for logging in to the Autofill service.
 * It is launched when an Autofill Response or specific Dataset within the Response requires
 * authentication to access. It bundles the result in an Intent.
 */
public class AuthActivity extends Activity {

    // Unique id for dataset intents.
    private static int sDatasetPendingIntentId = 0;

    private EditText mMasterPassword;
    private Button mCancel;
    private Button mLogin;
    private Intent mReplyIntent;

    static IntentSender getAuthIntentSenderForResponse(Context context) {
        final Intent intent = new Intent(context, AuthActivity.class);
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT)
                .getIntentSender();
    }

    static IntentSender getAuthIntentSenderForDataset(Context context, String datasetName) {
        final Intent intent = new Intent(context, AuthActivity.class);
        intent.putExtra(EXTRA_DATASET_NAME, datasetName);
        intent.putExtra(EXTRA_FOR_RESPONSE, false);
        return PendingIntent.getActivity(context, ++sDatasetPendingIntentId, intent,
                PendingIntent.FLAG_CANCEL_CURRENT).getIntentSender();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.auth_activity);
        mCancel = findViewById(R.id.cancel);
        mLogin = findViewById(R.id.login);
        mMasterPassword = findViewById(R.id.master_password);
        mLogin.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                login();
            }

        });

        mCancel.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onFailure();
                AuthActivity.this.finish();
            }
        });
    }

    private void login() {
        Editable password = mMasterPassword.getText();
        if (password.toString()
                .equals(MyPreferences.getInstance(AuthActivity.this).getMasterPassword())) {
            onSuccess();
        } else {
            Toast.makeText(this, "Password incorrect", Toast.LENGTH_SHORT).show();
            onFailure();
        }
        finish();
    }

    @Override
    public void finish() {
        if (mReplyIntent != null) {
            setResult(RESULT_OK, mReplyIntent);
        } else {
            setResult(RESULT_CANCELED);
        }
        super.finish();
    }

    private void onFailure() {
        Log.w(TAG, "Failed auth.");
        mReplyIntent = null;
    }

    private void onSuccess() {
        Intent intent = getIntent();
        boolean forResponse = intent.getBooleanExtra(EXTRA_FOR_RESPONSE, true);
        AssistStructure structure = intent.getParcelableExtra(EXTRA_ASSIST_STRUCTURE);
        StructureParser parser = new StructureParser(structure);
        parser.parse();
        AutofillFieldsCollection autofillFields = parser.getAutofillFields();
        int saveTypes = parser.getSaveTypes();
        mReplyIntent = new Intent();
        HashMap<String, ClientFormData> clientFormDataMap =
                LocalAutofillRepository.getInstance(this).getClientFormData
                        (autofillFields.getFocusedHints(), autofillFields.getAllHints());
        if (forResponse) {
            setResponseIntent(AutofillHelper.newResponse
                    (this, false, autofillFields, saveTypes, clientFormDataMap));
        } else {
            String datasetName = intent.getStringExtra(EXTRA_DATASET_NAME);
            setDatasetIntent(AutofillHelper.newDataset
                    (this, autofillFields, clientFormDataMap.get(datasetName)));
        }
    }

    private void setResponseIntent(FillResponse fillResponse) {
        mReplyIntent.putExtra(EXTRA_AUTHENTICATION_RESULT, fillResponse);
    }

    private void setDatasetIntent(Dataset dataset) {
        mReplyIntent.putExtra(EXTRA_AUTHENTICATION_RESULT, dataset);
    }
}
