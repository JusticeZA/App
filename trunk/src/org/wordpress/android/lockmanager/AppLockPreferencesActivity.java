package org.wordpress.android.lockmanager;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

import org.wordpress.android.R;

public class AppLockPreferencesActivity extends Activity implements OnClickListener {

    private EditText mPasswordField = null;
    private Button mSaveButton = null;
    private CheckBox mEnabledCheckBox = null;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.app_lock_preferences);
        mPasswordField = (EditText) findViewById(R.id.password);
        mSaveButton = (Button) findViewById(R.id.save);
        mSaveButton.setOnClickListener(this);
        mEnabledCheckBox = (CheckBox) findViewById(R.id.applock_checkbox);
        mEnabledCheckBox.setChecked(AppLockManager.getInstance().getCurrentAppLock().isPasswordLocked());
    }

    /*
    @Override
    public void onBackPressed() {
        finish();
    }
*/
    @Override
    public void onClick(View arg0) {
        String pinCode = null;
        if( mEnabledCheckBox.isChecked() )
            pinCode = mPasswordField.getText().toString().trim();

        AppLockManager.getInstance().getCurrentAppLock().setPassword(pinCode);

        finish();
    }

}