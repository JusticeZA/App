package com.roundhill.androidWP;

import java.util.HashMap;
import java.util.Vector;
import org.apache.commons.*;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnCreateContextMenuListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;


public class notificationSettings extends Activity {
    /** Called when the activity is first created. */
	public Vector accounts;
	public Vector accountNames = new Vector();
	private String selectedID = "";
	public int checkCtr = 0;
	protected static Intent svc = null;
	
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
      
        displayAccounts();

    }
    
public void displayAccounts(){
	//settings time!
    final settingsDB settingsDB = new settingsDB(this);
	accounts = settingsDB.getAccounts(this);
	
	
	if (accounts.size() > 0){
		ScrollView sv = new ScrollView(this);
		sv.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT,
                LayoutParams.WRAP_CONTENT));
		sv.setBackgroundColor(Color.parseColor("#e8e8e8"));
		LinearLayout layout = new LinearLayout(this);
		
		layout.setPadding(10, 10, 10, 0);
		layout.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT,
                LayoutParams.WRAP_CONTENT));

		layout.setOrientation(LinearLayout.VERTICAL);
		layout.setGravity(Gravity.LEFT);
		
		final LinearLayout cbLayout = new LinearLayout(this);
		
		cbLayout.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT,
                LayoutParams.WRAP_CONTENT));

		cbLayout.setOrientation(LinearLayout.VERTICAL);
		
        TextView textView = new TextView(this);
        textView.setTextColor(Color.parseColor("#444444"));
        textView.setTextSize(12);
        textView.setPadding(0, 20, 0, 0);
        textView.setGravity(Gravity.CENTER_HORIZONTAL);
        textView.setText("Select which blogs to receive comment notifications:");

        layout.addView(textView);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams 
        (LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        
        for (int i = 0; i < accounts.size(); i++) {
            
        	HashMap curHash = (HashMap) accounts.get(i);
        	String curBlogName = curHash.get("blogName").toString();
        	String curUsername = curHash.get("username").toString();
        	String accountID = curHash.get("id").toString();
        	int runService = Integer.valueOf(curHash.get("runService").toString());
        	accountNames.add(i, curBlogName);
        	cbLayout.setBackgroundColor(Color.parseColor("#e8e8e8"));
            
            final CheckBox checkBox = new CheckBox(this);
            checkBox.setTextColor(Color.parseColor("#444444"));
            checkBox.setTextSize(18);
            checkBox.setText(escapeUtils.unescapeHtml(curBlogName));
            checkBox.setId(Integer.valueOf(accountID));
            params.setMargins(0, 0, 0, 6);
            checkBox.setButtonDrawable(R.layout.btn_check);
            checkBox.setLayoutParams(params);
            
            if (runService == 1){
            	checkBox.setChecked(true);
            }

            cbLayout.addView(checkBox);  
        } 
        
        if (cbLayout.getChildCount() > 0){
        	layout.addView(cbLayout);
        }
        
        //add spinner and buttons
        TextView textView2 = new TextView(this);
        textView2.setTextColor(Color.parseColor("#444444"));
        textView2.setTextSize(12);
        textView2.setPadding(0, 20, 0, 0);
        textView2.setGravity(Gravity.CENTER_HORIZONTAL);
        textView2.setText("Update interval:");

        layout.addView(textView2);
        
        final Spinner sInterval = new Spinner(this);
        sInterval.setLayoutParams(params);
        sInterval.setBackgroundResource(R.layout.spinner_selector);
	    ArrayAdapter<Object> sIntervalArrayAdapter = new ArrayAdapter<Object>(this,
	    		R.layout.spinner_textview,
	            new String[] { "5 Minutes", "10 Minutes", "15 Minutes", "30 Minutes" , "1 Hour", "3 Hours", "6 Hours", "12 Hours", "Daily"});
	    sInterval.setAdapter(sIntervalArrayAdapter);
	    
	    String interval = settingsDB.getInterval(this);
	    
	    if (interval != ""){
            sInterval.setSelection(sIntervalArrayAdapter.getPosition(interval));
	    }
	    
	    layout.addView(sInterval);
	    
	    LinearLayout.LayoutParams params2 = new LinearLayout.LayoutParams 
        (LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.FILL_PARENT);
	    
	    final customButton save = new customButton(this);
	    save.setLayoutParams(params2);
	    save.setTextColor(Color.parseColor("#444444"));
        save.setTextSize(18);
        save.setWidth(80);
        save.setHeight(60);
	    save.setText("Save");
	    
	    save.setOnClickListener(new customButton.OnClickListener() {
            public void onClick(View v) {
            	
            	int listItemCount = cbLayout.getChildCount();
            	for( int i=0;i<listItemCount;i++ ) {
            	    CheckBox cbox = (CheckBox) ((View)cbLayout.getChildAt(i));
            	    int id = cbox.getId();
            	    if( cbox.isChecked() ) {   
            	    	checkCtr++;
            	        settingsDB.updateNotificationFlag(notificationSettings.this, id, true);
            	        Log.i("CommentService", "Service enabled for " + cbox.getText());
            	    }
            	    else{
            	        settingsDB.updateNotificationFlag(notificationSettings.this, id, false);
            	    }

            	}
            	
            	settingsDB.updateInterval(notificationSettings.this, sInterval.getSelectedItem().toString());
            	
            	if (checkCtr > 0){
            		//notification testing
        			if (svc != null){
            			stopService(svc);
            			}
        	        svc = new Intent(notificationSettings.this, commentService.class);
        	        svc.putExtra("updateInterval", sInterval.getSelectedItem().toString());
        	        startService(svc);
        	        
        	        /*NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE); 
        			  Notification notification = new Notification(R.drawable.logo, "Timer has cycled!", System.currentTimeMillis());
        			  notificationManager.notify(01234, notification); */
        			  
        	        
        		  		
        		  	//Toast.makeText(settings.this, "Comment Notification Service Started", Toast.LENGTH_LONG);
        	        
        	        commentService.setUpdateListener(new ServiceUpdateUIListener() {
        	            public void updateUI(final String accountID, final String accountName) {
        	              // make sure this runs in the UI thread... since it's messing with views...
        	              runOnUiThread(
        	                  new Runnable() {
        	                    public void run() {
        	                    	
        	                    	final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        	        		  		ComponentName comp = new ComponentName(getPackageName(), getClass().getName());
        	        		  		Intent notificationIntent = new Intent(notificationSettings.this, moderateComments.class);
        	        		  		notificationIntent.putExtra("id", accountID);
        	        		  		notificationIntent.putExtra("accountName", accountName);
        	        		  		notificationIntent.putExtra("fromNotification", true);
        	        		  		final PendingIntent pendingIntent = PendingIntent.getActivity(notificationSettings.this, 0, notificationIntent, 0);
        	                    	
        	     			  		Notification n = new Notification(R.drawable.wp_logo_small, "New Comment Received", System.currentTimeMillis());
        	     			  		n.setLatestEventInfo(notificationSettings.this, accountName, "New Comment Received", pendingIntent);
        	     			  		nm.notify(22 + Integer.valueOf(accountID), n); //needs a unique id
        	     			  		

        	                    }
        	                  });
        	            }
        	          });
        	        //notification testing
        		}
        		else{
        			if (svc != null){
        				
        				Vector activeNotificationAccounts = settingsDB.getNotificationAccounts(notificationSettings.this);
        				if (activeNotificationAccounts != null){
        					if (activeNotificationAccounts.size() < 1){
        						stopService(svc);  //shut down the service
        					}
        				}
        				
        			
        			}
        			//Toast.makeText(settings.this, "Comment Notification Service Stopped", Toast.LENGTH_LONG);

        		
            	}
            	
            	finish();
            }
        });
	    
	    
	    layout.addView(save);
        
        sv.addView(layout);
        
        setContentView(sv);
	}
}


}





