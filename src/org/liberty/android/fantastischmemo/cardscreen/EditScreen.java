/*
Copyright (C) 2010 Haowen Ning

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

*/
package org.liberty.android.fantastischmemo.cardscreen;

import org.liberty.android.fantastischmemo.*;
import org.liberty.android.fantastischmemo.tts.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Date;


import android.graphics.Color;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.os.Bundle;
import android.os.Environment;
import android.content.Context;
import android.preference.PreferenceManager;
import android.text.Html;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.ContextMenu;
import android.view.MotionEvent;
import android.view.View;
import android.view.Display;
import android.view.WindowManager;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.ImageButton;
import android.os.Handler;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Toast;
import android.util.Log;
import android.os.SystemClock;
import android.net.Uri;
import android.database.SQLException;

import android.gesture.Gesture;
import android.gesture.GestureLibraries;
import android.gesture.GestureLibrary;
import android.gesture.GestureOverlayView;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.gesture.Prediction;
import android.gesture.GestureOverlayView.OnGesturePerformedListener;

public class EditScreen extends AMActivity{
    private final static String TAG = "org.liberty.android.fantastischmemo.cardscreen.EditScreen";
    private AnyMemoTTS questionTTS = null;
    private AnyMemoTTS answerTTS = null;
    private final int DIALOG_LOADING_PROGRESS = 100;
    private final int ACTIVITY_FILTER = 10;
    private final int ACTIVITY_EDIT = 11;
    private final int ACTIVITY_CARD_TOOLBOX = 12;
    private final int ACTIVITY_DB_TOOLBOX = 13;
    private final int ACTIVITY_GOTO_PREV = 14;
    private final int ACTIVITY_SETTINGS = 15;

    Handler mHandler;
    Item currentItem = null;
    Item savedItem = null;
    Item prevItem = null;
    String dbPath = "";
    String dbName = "";
    String activeFilter = "";
    FlashcardDisplay flashcardDisplay;
    SettingManager settingManager;
    ControlButtons controlButtons;
    private GestureDetector gestureDetector;
    ItemManager itemManager;

    @Override
	public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
		setContentView(R.layout.memo_screen_layout);
        mHandler = new Handler();
        Bundle extras = getIntent().getExtras();
        int currentId = 1;
        if (extras != null) {
            dbPath = extras.getString("dbpath");
            dbName = extras.getString("dbname");
            activeFilter = extras.getString("active_filter");
            currentId = extras.getInt("id", 1);
        }
        try{
            settingManager = new SettingManager(this, dbPath, dbName);
            flashcardDisplay = new FlashcardDisplay(this, settingManager);
            controlButtons = new EditScreenButtons(this);
            itemManager = new ItemManager.Builder(this, dbPath, dbName)
                .setFilter(activeFilter)
                .build();

            initTTS();
            composeViews();
            currentItem = itemManager.getItem(currentId);
            flashcardDisplay.updateView(currentItem);
            updateTitle();
            setButtonListeners();
            gestureDetector= new GestureDetector(EditScreen.this, gestureListener);
            if(gestureDetector == null){
                Log.e(TAG, "NULL GESTURE DETECTOR");
            }
            flashcardDisplay.setScreenOnTouchListener(viewTouchListener);
            registerForContextMenu(flashcardDisplay.getView());
            /* Run the learnQueue init in a separate thread */
        }
        catch(Exception e){
            Log.e(TAG, "Error in the onCreate()", e);
            AMGUIUtility.displayException(this, getString(R.string.open_database_error_title), getString(R.string.open_database_error_message), e);
        }

    }

    @Override
    public void onDestroy(){
        if(itemManager != null){
            itemManager.close();
        }
        if(settingManager != null){
            settingManager.close();
        }
        if(questionTTS != null){
            questionTTS.shutdown();
        }
        if(answerTTS != null){
            answerTTS.shutdown();
        }
        super.onDestroy();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data){
        super.onActivityResult(requestCode, resultCode, data);
        if(resultCode ==Activity.RESULT_CANCELED){
            return;
        }
        switch(requestCode){
            case ACTIVITY_EDIT:
            {

                Bundle extras = data.getExtras();
                Item item = (Item)extras.getSerializable("item");
                if(item != null){
                    currentItem = item;
                }
                restartActivity();
                break;
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.edit_screen_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menuspeakquestion:
            {
                if(questionTTS != null && currentItem != null){
                    questionTTS.sayText(currentItem.getQuestion());
                }
                return true;
            }

            case R.id.menuspeakanswer:
            {
                if(answerTTS != null && currentItem != null){
                    answerTTS.sayText(currentItem.getQuestion());
                }
                return true;
            }

            case R.id.editmenu_settings_id:
            {
                Intent myIntent = new Intent(this, SettingsScreen.class);
                myIntent.putExtra("dbname", dbName);
                myIntent.putExtra("dbpath", dbPath);
                startActivityForResult(myIntent, ACTIVITY_SETTINGS);
                return true;
            }

            case R.id.editmenu_detail_id:
            {
                Intent myIntent = new Intent(this, DetailScreen.class);
                myIntent.putExtra("dbname", this.dbName);
                myIntent.putExtra("dbpath", this.dbPath);
                myIntent.putExtra("itemid", currentItem.getId());
                startActivityForResult(myIntent, 2);
                return true;
            }

            case R.id.menu_edit_filter:
            {
                Intent myIntent = new Intent(this, Filter.class);
                myIntent.putExtra("dbname", dbName);
                myIntent.putExtra("dbpath", dbPath);
                startActivityForResult(myIntent, ACTIVITY_FILTER);
                return true;
            }
        }

        return false;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo){
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.editscreen_context_menu, menu);
        menu.setHeaderTitle(R.string.menu_text);
    }

    @Override
    public boolean onContextItemSelected(MenuItem menuitem) {
        switch(menuitem.getItemId()) {
            case R.id.menu_context_copy:
            {
                savedItem = currentItem.clone();
                return true;
            }
            case R.id.menu_context_paste:
            {
                if(savedItem != null){
                    itemManager.insert(savedItem, currentItem.getId());
                    currentItem = savedItem;
                    flashcardDisplay.updateView(currentItem);
                    updateTitle();
                }

                return true;
            }

            default:
            {
                return super.onContextItemSelected(menuitem);
            }
        }
    }

    private void initTTS(){
        String audioDir = Environment.getExternalStorageDirectory().getAbsolutePath() + getString(R.string.default_audio_dir);
        Locale ql = settingManager.getQuestionAudioLocale();
        Locale al = settingManager.getAnswerAudioLocale();
        if(settingManager.getQuestionUserAudio()){
            questionTTS = new AudioFileTTS(audioDir, dbName);
        }
        else if(ql != null){
            if(settingManager.getEnableTTSExtended()){
                questionTTS = new AnyMemoTTSExtended(this, ql);
            }
            else{
                questionTTS = new AnyMemoTTSPlatform(this, ql);
            }
        }
        else{
            questionTTS = null;
        }
        if(settingManager.getAnswerUserAudio()){
            answerTTS = new AudioFileTTS(audioDir, dbName);
        }
        else if(al != null){
            if(settingManager.getEnableTTSExtended()){
                answerTTS = new AnyMemoTTSExtended(this, al);
            }
            else{
                answerTTS = new AnyMemoTTSPlatform(this, al);
            }
        }
        else{
            answerTTS = null;
        }
    }

    private void restartActivity(){
        Intent myIntent = new Intent(this, EditScreen.class);
        if(currentItem != null){
            myIntent.putExtra("id", currentItem.getId());
        }
        myIntent.putExtra("dbname", dbName);
        myIntent.putExtra("dbpath", dbPath);
        myIntent.putExtra("active_filter", activeFilter);
        finish();
        startActivity(myIntent);
    }


    private void composeViews(){
        LinearLayout memoRoot = (LinearLayout)findViewById(R.id.memo_screen_root);

        LinearLayout flashcardDisplayView = (LinearLayout)flashcardDisplay.getView();
        LinearLayout controlButtonsView = (LinearLayout)controlButtons.getView();
        /* 
         * -1: Match parent -2: Wrap content
         * This is necessary or the view will not be 
         * stetched
         */
        memoRoot.addView(flashcardDisplayView, -1, -1);
        memoRoot.addView(controlButtonsView, -1, -2);
        flashcardDisplayView.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT, 1.0f));
    }

    void setButtonListeners(){
        Map<String, Button> bm = controlButtons.getButtons();
        Button newButton = bm.get("new");
        Button editButton = bm.get("edit");
        Button prevButton = bm.get("prev");
        Button nextButton = bm.get("next");
        newButton.setOnClickListener(newButtonListener);
        editButton.setOnClickListener(editButtonListener);
        prevButton.setOnClickListener(prevButtonListener);
        nextButton.setOnClickListener(nextButtonListener);

    }

    private View.OnClickListener newButtonListener = new View.OnClickListener(){
        public void onClick(View v){
            Intent myIntent = new Intent(EditScreen.this, CardEditor.class);
            myIntent.putExtra("dbpath", dbPath);
            myIntent.putExtra("dbname", dbName);
            startActivityForResult(myIntent, ACTIVITY_EDIT);
        }
    };

    private View.OnClickListener editButtonListener = new View.OnClickListener(){
        public void onClick(View v){
            Intent myIntent = new Intent(EditScreen.this, CardEditor.class);
            myIntent.putExtra("item", currentItem);
            myIntent.putExtra("dbpath", dbPath);
            myIntent.putExtra("dbname", dbName);
            startActivityForResult(myIntent, ACTIVITY_EDIT);
        }
    };

    private void updateTitle(){
        if(currentItem != null){
            setTitle(getString(R.string.memo_current_id) + " " + currentItem.getId());
        }
    }
    
    private void gotoNext(){
        currentItem = itemManager.getNextItem(currentItem);
        flashcardDisplay.updateView(currentItem);
        updateTitle();
    }

    private void deleteCurrent(){
        if(currentItem != null){
            currentItem = itemManager.deleteItem(currentItem);
        }
        restartActivity();
    }

    private void gotoPrev(){
        currentItem = itemManager.getPreviousItem(currentItem);
        flashcardDisplay.updateView(currentItem);
        updateTitle();
    }

    private View.OnClickListener prevButtonListener = new View.OnClickListener(){
        public void onClick(View v){
            gotoPrev();
        }
    };

    private View.OnClickListener nextButtonListener = new View.OnClickListener(){
        public void onClick(View v){
            gotoNext();
        }
    };

    private View.OnTouchListener viewTouchListener = new View.OnTouchListener(){
        @Override
        public boolean onTouch(View v, MotionEvent event){
            return gestureDetector.onTouchEvent(event);
        }
    };


    private GestureDetector.OnGestureListener gestureListener = new GestureDetector.SimpleOnGestureListener(){
        private static final int SWIPE_MIN_DISTANCE = 120;
        private static final int SWIPE_MAX_OFF_PATH = 250;
        private static final int SWIPE_THRESHOLD_VELOCITY = 200;

        @Override 
        public boolean onDown(MotionEvent e){
            /* Trick: Prevent the menu to popup twice */
            return true;
        }
        @Override 
        public void onLongPress(MotionEvent e){
            closeContextMenu();
            EditScreen.this.openContextMenu(flashcardDisplay.getView());
            Log.v(TAG, "Open Menu!");
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            try {
                if (Math.abs(e1.getY() - e2.getY()) > SWIPE_MAX_OFF_PATH)
                    return false;
                if(e1.getX() - e2.getX() > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                    /* Swipe Right to Left event */
                    gotoNext();
                }  else if (e2.getX() - e1.getX() > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                    /* Swipe Left to Right event */
                    gotoPrev();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error handling gesture left/right event", e);
            }
            return false;
        }
    };

}

