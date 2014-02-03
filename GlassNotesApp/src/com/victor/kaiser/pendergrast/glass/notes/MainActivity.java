package com.victor.kaiser.pendergrast.glass.notes;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.glass.media.Sounds;
import com.google.android.glass.widget.CardScrollView;
import com.victor.kaiser.pendergrast.glass.notes.api.GetNotesTask;
import com.victor.kaiser.pendergrast.glass.notes.api.NotesJsonParser;
import com.victor.kaiser.pendergrast.glass.notes.auth.AuthTokenJsonParser;
import com.victor.kaiser.pendergrast.glass.notes.auth.RefreshAuthTokenTask;
import com.victor.kaiser.pendergrast.glass.notes.content.NoteAdapter;
import com.victor.kaiser.pendergrast.glass.notes.preferences.PreferenceConstants;

public class MainActivity extends Activity  implements RefreshAuthTokenTask.OnGetTokenListener, OnItemClickListener {
	private static final String TAG = "MainActivity";

	private SharedPreferences mPrefs;

	private CardScrollView mScrollView;
	private NoteAdapter mAdapter;

	private boolean mNotesShown = false;
	private int mItemClicked = -1;

	private TextView mTitle;
	private TextView mSubtitle;
	private ImageView mImage;
	
	@Override
	protected void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);

		// Keep and turn the screen on
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);


		// A basic card-style layout with a 
		// full screen image in the background
		setContentView(R.layout.card_full_image);

		mTitle = (TextView) findViewById(R.id.card_title);
		mSubtitle = (TextView) findViewById(R.id.card_subtitle);
		mImage = (ImageView) findViewById(R.id.card_image);

		// This is a fairly generic layout,
		// one customization this app does is it moves
		// the text from being left aligned to the center
		mTitle.setGravity(Gravity.CENTER);

		// Show a "Loading..." message
		mTitle.setText(getString(R.string.text_signing_in));
		
		mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		
		// Check to make sure that there is a Refresh Token
		if(mPrefs.getString(PreferenceConstants.REFRESH_TOKEN, "").isEmpty()){
			// Launch the authentication immersion
			Intent authSetupIntent  = new Intent(this, AuthActivity.class);
			startActivity(authSetupIntent);
			finish();
		}
		
		// Get a new auth token 
		// Once an auth token is recieved,
		// get the notes from the server
		RefreshAuthTokenTask refreshAuthTask = new RefreshAuthTokenTask();
		refreshAuthTask.setListener(this);
		refreshAuthTask.execute(mPrefs.getString(PreferenceConstants.REFRESH_TOKEN, ""));
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event){
		// Open the menu when taps are detected
		if(keyCode == KeyEvent.KEYCODE_DPAD_CENTER){
			playClickSound();
			openOptionsMenu();
			return true;
		}

		return super.onKeyDown(keyCode, event);
	}

	@Override
	public void onItemClick(AdapterView<?> adapterView, View view, int index, long id){
		// An item was clicked
		playClickSound();

		// Keep the index of the click for possible
		// later use
		mItemClicked = index;

		// Invalidate the menu: an item has been
		// clicked, so we know that the CardScrollView
		// is being shown and that there are notes
		invalidateOptionsMenu();
		openOptionsMenu();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu
		getMenuInflater().inflate(R.menu.main, menu);

		// If the CardScrollView is shown,
		// then there should be a "Delete Note" option
		// The "Delete Note" item defaults to not bein
		// visible
		if(mNotesShown){
			MenuItem delete = menu.findItem(R.id.menu_delete_note);
			delete.setVisible(true);
		}

		return true;
	}


	@Override
	public boolean onOptionsItemSelected(MenuItem item){
		switch(item.getItemId()){
		case R.id.menu_delete_note:
			
			break;
		case R.id.menu_add_note:
			// Go to the AddActivity to add notes
			Intent addIntent = new Intent(this, AddActivity.class);
			finish();
			startActivity(addIntent);
			break;
		case R.id.menu_sign_out:
			// Clear all the saved tokens and codes
			mPrefs.edit()
				.putString(PreferenceConstants.AUTH_TOKEN, "")
				.putString(PreferenceConstants.REFRESH_TOKEN, "")
				.putString(PreferenceConstants.DEVICE_CODE, "")
				.commit();
			
			// Start the AuthActivity to do setup again
			Intent authSetupIntent = new Intent(this, AuthActivity.class);
			startActivity(authSetupIntent);

			// Close this Activity
			finish();
			break;
		}

		return super.onOptionsItemSelected(item);
	}

	/**
	 * This is the listener for the RefreshAuthTokenTask
	 * that is started in onCreate
	 */
	@Override
	public void onResponse(boolean success, String response) {
		if(success){
			AuthTokenJsonParser parser = new AuthTokenJsonParser(response);
			
			// There may have been a response
			// with an error instead of an
			// auth token
			if(parser.hasError()){
				Log.e(TAG, parser.getError());
				displayFailureToSignIn();
			}else{
				// Show a "Getting notes" View
				displayGettingNotes();
				
				// Save this auth token for possible future use
				parser.writeToPreferences(mPrefs);
				String authToken = parser.getAuthToken();

				// Now sync the notes with those on the server
				GetNotesTask task = new GetNotesTask();
				task.setListener(new GetNotesTask.OnGetNotesListener(){
					@Override
					public void onReceiveNotes(boolean success, String response){
						Log.i(TAG, "onGetNotes: " + success);
						if(success){
							// Display the notes contained in the response,
							// First extract the notes from the response
							NotesJsonParser notesParser = new NotesJsonParser(response);
							String notes = notesParser.getNotes();

							if(notes.isEmpty()){
								displayNoNotes();
							}else{
								// Show the notes in a 
								// CardScrollView
								displayNotes(notes);
							}

						}else{
							// Failed to get the notes
							displayFailureToSync();
						}
					}
				});
				
				// GetNotesTask takes the auth token
				// as a parameter in its execution
				task.execute(authToken);
			}
		}else{
			// success was false for whatever reason,
			// so display a failed to sign-in message
			displayFailureToSignIn();
		}
	}

	private void displayGettingNotes(){
		// Show getting notes
		mTitle.setText(getString(R.string.text_getting_notes));
		mSubtitle.setText("");
		mImage.setImageDrawable(null);
	}

	private void displayFailureToSignIn(){
		// Show failure to sign in
		mTitle.setText(getString(R.string.text_failed_to_sign_in));
		mSubtitle.setText(getString(R.string.text_check_internet));
		mImage.setImageResource(R.drawable.ic_warning_50);
	}

	private void displayFailureToSync(){
		// Show failure to sync
		mTitle.setText(getString(R.string.text_failed_to_get_notes));
		mSubtitle.setText(getString(R.string.text_check_internet));
		mImage.setImageResource(R.drawable.ic_warning_50);
	}
	
	private void displayNoNotes(){
		// Show failure to sync
		mTitle.setText(getString(R.string.text_no_notes));
		mSubtitle.setText(getString(R.string.text_suggest_add_notes));
		mImage.setImageResource(R.drawable.ic_pen_50);
	}

	private void displayNotes(String notes){
		mScrollView = new CardScrollView(this);
		
		// Create the adapter and set it, allow interaction
		mAdapter = new NoteAdapter(this, notes);
		mScrollView.setAdapter(mAdapter);

		// Set the OnClickListener
		mScrollView.setOnItemClickListener(this);

		// Show the CardScrollView
		mScrollView.activate();
		setContentView(mScrollView);

		mNotesShown = true;
	}

	/**
	 * Play the standard Glass tap sound
	 */
	protected void playClickSound() {
		AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		audio.playSoundEffect(Sounds.TAP);
	}

}

