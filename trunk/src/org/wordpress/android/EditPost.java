package org.wordpress.android;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Vector;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.wordpress.android.models.Blog;
import org.wordpress.android.models.MediaFile;
import org.wordpress.android.models.Post;
import org.wordpress.android.util.EscapeUtils;
import org.wordpress.android.util.ImageHelper;
import org.wordpress.android.util.LocationHelper;
import org.wordpress.android.util.LocationHelper.LocationResult;
import org.wordpress.android.util.StringHelper;
import org.wordpress.android.util.WPEditText;
import org.wordpress.android.util.WPHtml;
import org.wordpress.android.util.WPImageSpan;
import org.xmlrpc.android.ApiHelper;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Address;
import android.location.Criteria;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.format.DateUtils;
import android.text.style.AlignmentSpan;
import android.text.style.CharacterStyle;
import android.view.Display;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

public class EditPost extends Activity {
	/** Called when the activity is first created. */
	public ProgressDialog pd;
	Vector<String> selectedCategories = new Vector<String>();
	public Boolean newStart = true;
	public String categoryErrorMsg = "", accountName = "", option, provider;
	private JSONArray categories;
	private int id;
	long postID, customPubDate = 0;
	private int ID_DIALOG_DATE = 0, ID_DIALOG_TIME = 1, ID_DIALOG_LOADING = 2;
	public Boolean localDraft = false, isPage = false, isNew = false,
			isAction = false, isUrl = false, isLargeScreen = false,
			isCustomPubDate = false;
	Criteria criteria;
	Location curLocation;
	ProgressDialog postingDialog;
	int styleStart = -1, cursorLoc = 0, screenDensity = 0;
	// date holders
	private int mYear, mMonth, mDay, mHour, mMinute;
	private Blog blog;
	private Post post;
	// post formats
	String[] postFormats;
	String[] postFormatTitles = null;
	LocationHelper locationHelper;

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		Bundle extras = getIntent().getExtras();

		// need to make sure we have db and currentBlog on views that don't use
		// the Action Bar
		if (WordPress.wpDB == null)
			WordPress.wpDB = new WordPressDB(this);
		if (WordPress.currentBlog == null) {
			try {
				WordPress.currentBlog = new Blog(
						WordPress.wpDB.getLastBlogID(this), this);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE))
				.getDefaultDisplay();
		int width = display.getWidth();
		int height = display.getHeight();
		if (height > width) {
			width = height;
		}
		if (width > 480) {
			isLargeScreen = true;
		}

		categories = new JSONArray();
		String action = getIntent().getAction();

		if (Intent.ACTION_SEND.equals(action)
				|| Intent.ACTION_SEND_MULTIPLE.equals(action)) {
			// we arrived here from a share action
			WordPress.richPostContent = null;
			isAction = true;
			isNew = true;
			Vector<?> accounts = WordPress.wpDB.getAccounts(this);

			if (accounts.size() > 0) {

				final String blogNames[] = new String[accounts.size()];
				final int accountIDs[] = new int[accounts.size()];

				for (int i = 0; i < accounts.size(); i++) {

					HashMap<?, ?> curHash = (HashMap<?, ?>) accounts.get(i);
					try {
						blogNames[i] = EscapeUtils.unescapeHtml(curHash.get(
								"blogName").toString());
					} catch (Exception e) {
						blogNames[i] = curHash.get("url").toString();
					}
					accountIDs[i] = (Integer) curHash.get("id");
					try {
						blog = new Blog(accountIDs[i], EditPost.this);
					} catch (Exception e) {
						Toast.makeText(
								this,
								getResources().getText(R.string.blog_not_found),
								Toast.LENGTH_SHORT).show();
						finish();
					}

				}

				// Don't prompt if they have one blog only
				if (accounts.size() != 1) {
					AlertDialog.Builder builder = new AlertDialog.Builder(this);
					builder.setCancelable(false);
					builder.setTitle(getResources().getText(
							R.string.select_a_blog));
					builder.setItems(blogNames,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int item) {
									id = accountIDs[item];
									try {
										blog = new Blog(id, EditPost.this);
									} catch (Exception e) {
										Toast.makeText(
												EditPost.this,
												getResources()
														.getText(
																R.string.blog_not_found),
												Toast.LENGTH_SHORT).show();
										finish();
									}
									WordPress.currentBlog = blog;
									WordPress.wpDB
											.updateLastBlogID(WordPress.currentBlog
													.getId());
									accountName = blogNames[item];
									setTitle(EscapeUtils
											.unescapeHtml(accountName)
											+ " - "
											+ getResources()
													.getText(
															(isPage) ? R.string.new_page
																	: R.string.new_post));
								}
							});
					AlertDialog alert = builder.create();
					alert.show();
				} else {
					id = accountIDs[0];
					try {
						blog = new Blog(id, EditPost.this);
					} catch (Exception e) {
						Toast.makeText(
								this,
								getResources().getText(R.string.blog_not_found),
								Toast.LENGTH_SHORT).show();
						finish();
					}
					WordPress.currentBlog = blog;
					WordPress.wpDB.updateLastBlogID(WordPress.currentBlog
							.getId());
					accountName = blogNames[0];
					setTitle(EscapeUtils.unescapeHtml(accountName)
							+ " - "
							+ getResources().getText(
									(isPage) ? R.string.new_page
											: R.string.new_post));
				}
			} else {
				// no account, load main view to load new account view
				Intent i = new Intent(this, Dashboard.class);
				Toast.makeText(getApplicationContext(),
						getResources().getText(R.string.no_account),
						Toast.LENGTH_LONG).show();
				startActivity(i);
				finish();
				return;
			}

		} else {

			if (extras != null) {
				id = WordPress.currentBlog.getId();
				try {
					blog = new Blog(id, this);
				} catch (Exception e) {
					Toast.makeText(this,
							getResources().getText(R.string.blog_not_found),
							Toast.LENGTH_SHORT).show();
					finish();
				}
				accountName = EscapeUtils.unescapeHtml(extras
						.getString("accountName"));
				postID = extras.getLong("postID");
				localDraft = extras.getBoolean("localDraft", false);
				isPage = extras.getBoolean("isPage", false);
				isNew = extras.getBoolean("isNew", false);
				option = extras.getString("option");

				if (extras.getBoolean("isQuickPress")) {
					id = extras.getInt("id");
					try {
						blog = new Blog(id, this);
						WordPress.currentBlog = blog;
					} catch (Exception e) {
						Toast.makeText(
								this,
								getResources().getText(R.string.blog_not_found),
								Toast.LENGTH_LONG).show();
						finish();
						return;
					}
				}

				if (!isNew) {
					try {
						post = new Post(id, postID, isPage, this);
						if (post == null) {
							// big oopsie
							Toast.makeText(
									this,
									getResources().getText(
											R.string.post_not_found),
									Toast.LENGTH_LONG).show();
							finish();
							return;
						} else {
							WordPress.currentPost = post;
						}
					} catch (Exception e) {
						finish();
					}
				}
			}

			if (isNew) {
				setTitle(EscapeUtils.unescapeHtml(WordPress.currentBlog
						.getBlogName())
						+ " - "
						+ getResources().getText(
								(isPage) ? R.string.new_page
										: R.string.new_post));
			} else {
				setTitle(EscapeUtils.unescapeHtml(WordPress.currentBlog
						.getBlogName())
						+ " - "
						+ getResources().getText(
								(isPage) ? R.string.edit_page
										: R.string.edit_post));
			}
		}

		if (isPage) {
			setContentView(R.layout.edit_page);
		} else {
			setContentView(R.layout.edit);
			if (blog.getPostFormats().equals("")) {
				Vector<Object> args = new Vector<Object>();
				args.add(blog);
				args.add(this);
				new ApiHelper.getPostFormatsTask().execute(args);
				postFormatTitles = getResources().getStringArray(
						R.array.post_formats_array);
				String defaultPostFormatTitles[] = { "aside", "audio", "chat",
						"gallery", "image", "link", "quote", "standard",
						"status", "video" };
				postFormats = defaultPostFormatTitles;
			} else {
				try {
					JSONObject jsonPostFormats = new JSONObject(
							blog.getPostFormats());
					postFormats = new String[jsonPostFormats.length()];
					postFormatTitles = new String[jsonPostFormats.length()];
					Iterator<?> it = jsonPostFormats.keys();
					int i = 0;
					while (it.hasNext()) {
						String key = (String) it.next();
						String val = (String) jsonPostFormats.get(key);
						postFormats[i] = key;
						postFormatTitles[i] = val;
						i++;
					}

					java.util.Arrays.sort(postFormats);
					java.util.Arrays.sort(postFormatTitles);

				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
			Spinner pfSpinner = (Spinner) findViewById(R.id.postFormat);
			ArrayAdapter<String> pfAdapter = new ArrayAdapter<String>(this,
					android.R.layout.simple_spinner_item, postFormatTitles);
			pfAdapter
					.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
			pfSpinner.setAdapter(pfAdapter);
			String activePostFormat = "standard";
			if (!isNew) {
				if (!post.getWP_post_format().equals(""))
					activePostFormat = post.getWP_post_format();
			}
			for (int i = 0; i < postFormats.length; i++) {
				if (postFormats[i].equals(activePostFormat))
					pfSpinner.setSelection(i);
			}

			if (Intent.ACTION_SEND.equals(action)
					|| Intent.ACTION_SEND_MULTIPLE.equals(action))
				setContent();

		}

		String[] items = new String[] {
				getResources().getString(R.string.publish_post),
				getResources().getString(R.string.draft),
				getResources().getString(R.string.pending_review),
				getResources().getString(R.string.post_private),
				getResources().getString(R.string.local_draft) };
		Spinner spinner = (Spinner) findViewById(R.id.status);
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_spinner_item, items);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(adapter);

		spinner.setOnItemSelectedListener(new OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> arg0, View arg1,
					int arg2, long arg3) {
				evaluateSaveButtonText();

			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
			}

		});

		boolean hasLocationProvider = false;
		LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
		List<String> providers = locationManager.getProviders(true);
		for (String providerName : providers) {
			if (providerName.equals(LocationManager.GPS_PROVIDER)
					|| providerName.equals(LocationManager.NETWORK_PROVIDER)) {
				hasLocationProvider = true;
			}
		}

		if (hasLocationProvider && blog.isLocation() && !isPage) {
			enableLBSButtons();
		}

		if (isNew) {

			// handles selections from the quick action bar
			if (option != null) {
				Intent i = new Intent(EditPost.this, EditContent.class);
				i.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
				i.putExtra("option", option);
				startActivityForResult(i, 0);
			}

		} else {
			EditText titleET = (EditText) findViewById(R.id.title);
			WPEditText contentET = (WPEditText) findViewById(R.id.postContent);
			EditText passwordET = (EditText) findViewById(R.id.post_password);

			titleET.setText(post.getTitle());

			if (post.isUploaded()) {
				items = new String[] {
						getResources().getString(R.string.publish_post),
						getResources().getString(R.string.draft),
						getResources().getString(R.string.pending_review),
						getResources().getString(R.string.post_private) };
				adapter = new ArrayAdapter<String>(this,
						android.R.layout.simple_spinner_item, items);
				spinner.setAdapter(adapter);
			}

			String contentHTML;

			if (!post.getMt_text_more().equals("")) {
				if (post.isLocalDraft())
					contentHTML = post.getDescription()
							+ "\n&lt;!--more--&gt;\n" + post.getMt_text_more();
				else
					contentHTML = post.getDescription() + "\n<!--more-->\n"
							+ post.getMt_text_more();
			} else {
				contentHTML = post.getDescription();
			}

			try {
				if (post.isLocalDraft()) {
					contentET.setText(WPHtml.fromHtml(
							contentHTML.replaceAll("\uFFFC", ""),
							EditPost.this, post));
				} else {
					contentET.setText(contentHTML.replaceAll("\uFFFC", ""));
				}

			} catch (Exception e1) {
				e1.printStackTrace();
			}

			long pubDate = post.getDate_created_gmt();
			if (pubDate != 0) {
				try {
					int flags = 0;
					flags |= android.text.format.DateUtils.FORMAT_SHOW_DATE;
					flags |= android.text.format.DateUtils.FORMAT_ABBREV_MONTH;
					flags |= android.text.format.DateUtils.FORMAT_SHOW_YEAR;
					flags |= android.text.format.DateUtils.FORMAT_SHOW_TIME;
					String formattedDate = DateUtils.formatDateTime(
							EditPost.this, pubDate, flags);
					TextView tvPubDate = (TextView) findViewById(R.id.pubDate);
					tvPubDate.setText(formattedDate);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			if (post.getWP_password() != null)
				passwordET.setText(post.getWP_password());

			if (post.getPost_status() != null) {
				String status = post.getPost_status();

				if (status.equals("publish")) {
					spinner.setSelection(0, true);
				} else if (status.equals("draft")) {
					spinner.setSelection(1, true);
				} else if (status.equals("pending")) {
					spinner.setSelection(2, true);
				} else if (status.equals("private")) {
					spinner.setSelection(3, true);
				} else if (status.equals("localdraft")) {
					spinner.setSelection(4, true);
				}

				evaluateSaveButtonText();
			}

			if (!isPage) {
				if (post.getCategories() != null) {
					categories = post.getCategories();
					if (!categories.equals("")) {

						for (int i = 0; i < categories.length(); i++) {
							try {
								selectedCategories.add(categories.getString(i));
							} catch (JSONException e) {
								e.printStackTrace();
							}
						}

						TextView tvCategories = (TextView) findViewById(R.id.selectedCategories);
						tvCategories.setText(getResources().getText(
								R.string.selected_categories)
								+ " " + getCategoriesCSV());

					}
				}

				Double latitude = post.getLatitude();
				Double longitude = post.getLongitude();

				if (latitude != 0.0) {
					new getAddressTask().execute(latitude, longitude);
				}

			}

			String tags = post.getMt_keywords();
			if (!tags.equals("")) {
				EditText tagsET = (EditText) findViewById(R.id.tags);
				tagsET.setText(tags);
			}
		}

		if (!isPage) {
			Button selectCategories = (Button) findViewById(R.id.selectCategories);

			selectCategories.setOnClickListener(new Button.OnClickListener() {
				public void onClick(View v) {

					Bundle bundle = new Bundle();
					bundle.putInt("id", id);
					if (categories.length() > 0) {
						bundle.putString("categoriesCSV", getCategoriesCSV());
					}
					Intent i = new Intent(EditPost.this, SelectCategories.class);
					i.putExtras(bundle);
					startActivityForResult(i, 1);
				}
			});
		}

		final WPEditText content = (WPEditText) findViewById(R.id.postContent);
		content.setOnFocusChangeListener(new View.OnFocusChangeListener() {
			@Override
			public void onFocusChange(View view, boolean hasFocus) {
				if (hasFocus) {
					Intent i = new Intent(EditPost.this, EditContent.class);
					if (isNew || localDraft)
						i.putExtra("localDraft", true);
					else
						i.putExtra("localDraft", false);
					WordPress.richPostContent = content.getText();
					i.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
					startActivityForResult(i, 0);
				}
			}
		});

		final Button saveButton = (Button) findViewById(R.id.post);

		saveButton.setOnClickListener(new Button.OnClickListener() {
			public void onClick(View v) {

				boolean result = savePost();
				if (result) {
					if (post.isUploaded()
							|| !post.getPost_status().equals("localdraft"))
						post.upload();
					finish();
				}
			}
		});

		Button pubDate = (Button) findViewById(R.id.pubDateButton);
		pubDate.setOnClickListener(new TextView.OnClickListener() {
			public void onClick(View v) {

				// get the current date
				Calendar c = Calendar.getInstance();
				mYear = c.get(Calendar.YEAR);
				mMonth = c.get(Calendar.MONTH);
				mDay = c.get(Calendar.DAY_OF_MONTH);
				mHour = c.get(Calendar.HOUR_OF_DAY);
				mMinute = c.get(Calendar.MINUTE);

				showDialog(ID_DIALOG_DATE);

			}
		});
	}

	private void evaluateSaveButtonText() {

		Spinner spinner = (Spinner) findViewById(R.id.status);
		Button saveButton = (Button) findViewById(R.id.post);
		if (spinner.getSelectedItemPosition() == 0)
			saveButton.setText(getResources().getText(R.string.publish_post));
		else
			saveButton.setText(getResources().getText(R.string.save));

	}

	public LocationResult locationResult = new LocationResult() {
		@Override
		public void gotLocation(Location location) {
			if (location != null) {
				curLocation = location;
				new getAddressTask().execute(curLocation.getLatitude(),
						curLocation.getLongitude());
			} else {
				TextView locationText = (TextView) findViewById(R.id.locationText);
				locationText.setText(getResources().getText(
						R.string.location_not_found));
			}
		}
	};

	private void enableLBSButtons() {
		locationHelper = new LocationHelper();

		RelativeLayout section4 = (RelativeLayout) findViewById(R.id.section4);
		section4.setVisibility(View.VISIBLE);

		final Button viewMap = (Button) findViewById(R.id.viewMap);
		viewMap.setOnClickListener(new TextView.OnClickListener() {
			public void onClick(View v) {

				Double latitude = 0.0;
				try {
					latitude = curLocation.getLatitude();
				} catch (Exception e) {
					e.printStackTrace();
				}
				if (latitude != 0.0) {
					String uri = "geo:" + latitude + ","
							+ curLocation.getLongitude();
					startActivity(new Intent(
							android.content.Intent.ACTION_VIEW, Uri.parse(uri)));
				} else {
					Toast.makeText(EditPost.this,
							getResources().getText(R.string.location_toast),
							Toast.LENGTH_SHORT).show();
				}

			}
		});

		Button updateLocation = (Button) findViewById(R.id.updateLocation);

		updateLocation.setOnClickListener(new Button.OnClickListener() {
			public void onClick(View v) {

				locationHelper.getLocation(EditPost.this, locationResult);
			}
		});

		Button removeLocation = (Button) findViewById(R.id.removeLocation);

		removeLocation.setOnClickListener(new Button.OnClickListener() {
			public void onClick(View v) {

				if (curLocation != null) {
					curLocation.setLatitude(0.0);
					curLocation.setLongitude(0.0);
				}
				if (post != null) {
					post.setLatitude(0.0);
					post.setLongitude(0.0);
				}

				TextView locationText = (TextView) findViewById(R.id.locationText);
				locationText.setText("");
			}
		});

		if (isNew) {
			locationHelper.getLocation(EditPost.this, locationResult);
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (resultCode == Activity.RESULT_CANCELED && requestCode != 1) {
			finish();
		} else if (data != null || requestCode == 4) {
			switch (requestCode) {
			case 0:
				if (WordPress.richPostContent != null) {
					WPEditText contentET = (WPEditText) findViewById(R.id.postContent);
					try {
						contentET.setText(WordPress.richPostContent);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}

				EditText title = (EditText) findViewById(R.id.title);
				title.requestFocus();

				break;
			case 1:
				Bundle extras = data.getExtras();
				String cats = extras.getString("selectedCategories");
				String[] splitCats = cats.split(",");
				categories = new JSONArray();
				for (int i = 0; i < splitCats.length; i++) {
					categories.put(splitCats[i]);
				}
				TextView selectedCategoriesTV = (TextView) findViewById(R.id.selectedCategories);
				selectedCategoriesTV.setText(getResources().getText(
						R.string.selected_categories)
						+ " " + getCategoriesCSV());

				break;
			}
		}
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		if (id == ID_DIALOG_DATE) {
			DatePickerDialog dpd = new DatePickerDialog(this, mDateSetListener,
					mYear, mMonth, mDay);
			dpd.setTitle("");
			return dpd;
		} else if (id == ID_DIALOG_TIME) {
			TimePickerDialog tpd = new TimePickerDialog(this, mTimeSetListener,
					mHour, mMinute, false);
			tpd.setTitle("");
			return tpd;
		} else if (id == ID_DIALOG_LOADING) {
			ProgressDialog loadingDialog = new ProgressDialog(this);
			loadingDialog.setMessage(getResources().getText(R.string.loading));
			loadingDialog.setIndeterminate(true);
			loadingDialog.setCancelable(true);
			return loadingDialog;
		}

		return super.onCreateDialog(id);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {

		super.onConfigurationChanged(newConfig);
	}

	public boolean savePost() {

		// grab the form data
		EditText titleET = (EditText) findViewById(R.id.title);
		String title = titleET.getText().toString();
		WPEditText contentET = (WPEditText) findViewById(R.id.postContent);

		String content = "";

		EditText passwordET = (EditText) findViewById(R.id.post_password);
		String password = passwordET.getText().toString();
		if (localDraft || isNew) {
			Editable e = contentET.getText();
			if (android.os.Build.VERSION.SDK_INT >= 14) {
				// remove suggestion spans, they cause craziness in
				// WPHtml.toHTML().
				CharacterStyle[] style = e.getSpans(0, e.length(),
						CharacterStyle.class);
				for (int i = 0; i < style.length; i++) {
					if (style[i].getClass().getName()
							.equals("android.text.style.SuggestionSpan"))
						e.removeSpan(style[i]);
				}
			}
			content = EscapeUtils.unescapeHtml(WPHtml.toHtml(e));
			// replace duplicate <p> tags so there's not duplicates, trac #86
			content = content.replace("<p><p>", "<p>");
			content = content.replace("</p></p>", "</p>");
			content = content.replace("<br><br>", "<br>");
			// sometimes the editor creates extra tags
			content = content.replace("</strong><strong>", "")
					.replace("</em><em>", "").replace("</u><u>", "")
					.replace("</strike><strike>", "")
					.replace("</blockquote><blockquote>", "");
		} else {
			content = contentET.getText().toString();
		}

		TextView tvPubDate = (TextView) findViewById(R.id.pubDate);
		String pubDate = tvPubDate.getText().toString();

		long pubDateTimestamp = 0;
		if (!pubDate.equals(getResources().getText(R.string.immediately))) {
			if (isCustomPubDate)
				pubDateTimestamp = customPubDate;
			else if (!isNew)
				pubDateTimestamp = post.getDate_created_gmt();
		}

		String tags = "", postFormat = "";
		if (!isPage) {
			EditText tagsET = (EditText) findViewById(R.id.tags);
			tags = tagsET.getText().toString();
			// post format
			Spinner postFormatSpinner = (Spinner) findViewById(R.id.postFormat);
			postFormat = postFormats[postFormatSpinner
					.getSelectedItemPosition()];
		}

		String images = "";
		boolean success = false;

		if (content.equals("")) {
			AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(
					EditPost.this);
			dialogBuilder.setTitle(getResources()
					.getText(R.string.empty_fields));
			dialogBuilder.setMessage(getResources().getText(
					R.string.title_post_required));
			dialogBuilder.setPositiveButton("OK",
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog,
								int whichButton) {
							// Just close the window
						}
					});
			dialogBuilder.setCancelable(true);
			dialogBuilder.create().show();
		} else {

			if (!isNew) {
				// update the images
				post.deleteMediaFiles();
				Editable s = contentET.getText();
				WPImageSpan[] click_spans = s.getSpans(0, s.length(),
						WPImageSpan.class);

				if (click_spans.length != 0) {

					for (int i = 0; i < click_spans.length; i++) {
						WPImageSpan wpIS = click_spans[i];
						images += wpIS.getImageSource().toString() + ",";

						MediaFile mf = new MediaFile();
						mf.setPostID(post.getId());
						mf.setTitle(wpIS.getTitle());
						mf.setCaption(wpIS.getCaption());
						mf.setDescription(wpIS.getDescription());
						mf.setFeatured(wpIS.isFeatured());
						mf.setFileName(wpIS.getImageSource().toString());
						mf.setHorizontalAlignment(wpIS.getHorizontalAlignment());
						mf.setWidth(wpIS.getWidth());
						mf.save(EditPost.this);

						int tagStart = s.getSpanStart(wpIS);
						s.removeSpan(wpIS);
						s.insert(tagStart, "<img android-uri=\""
								+ wpIS.getImageSource().toString() + "\" />");
						if (localDraft)
							content = EscapeUtils
									.unescapeHtml(WPHtml.toHtml(s));
						else
							content = s.toString();
					}
				}
			}

			Spinner spinner = (Spinner) findViewById(R.id.status);
			int selectedStatus = spinner.getSelectedItemPosition();
			String status = "";
			switch (selectedStatus) {
			case 0:
				status = "publish";
				break;
			case 1:
				status = "draft";
				break;
			case 2:
				status = "pending";
				break;
			case 3:
				status = "private";
				break;
			case 4:
				status = "localdraft";
				break;
			}

			Double latitude = 0.0;
			Double longitude = 0.0;
			if (blog.isLocation()) {

				// attempt to get the device's location
				try {
					latitude = curLocation.getLatitude();
					longitude = curLocation.getLongitude();
				} catch (Exception e) {
					e.printStackTrace();
				}

			}
			String needle = "<!--more-->";
			if (isNew) {
				post = new Post(id, title, content, images, pubDateTimestamp,
						categories.toString(), tags, status, password,
						latitude, longitude, isPage, postFormat, EditPost.this,
						true);
				post.setLocalDraft(true);

				// split up the post content if there's a more tag
				if (content.indexOf(needle) >= 0) {
					post.setDescription(content.substring(0,
							content.indexOf(needle)));
					post.setMt_text_more(content.substring(
							content.indexOf(needle) + needle.length(),
							content.length()));
				}

				success = post.save();

				post.deleteMediaFiles();

				Spannable s = contentET.getText();
				WPImageSpan[] image_spans = s.getSpans(0, s.length(),
						WPImageSpan.class);

				if (image_spans.length != 0) {

					for (int i = 0; i < image_spans.length; i++) {
						WPImageSpan wpIS = image_spans[i];
						images += wpIS.getImageSource().toString() + ",";

						MediaFile mf = new MediaFile();
						mf.setPostID(post.getId());
						mf.setTitle(wpIS.getTitle());
						mf.setCaption(wpIS.getCaption());
						// mf.setDescription(wpIS.getDescription());
						// mf.setFeatured(wpIS.isFeatured());
						mf.setFileName(wpIS.getImageSource().toString());
						mf.setFilePath(wpIS.getImageSource().toString());
						mf.setHorizontalAlignment(wpIS.getHorizontalAlignment());
						mf.setWidth(wpIS.getWidth());
						mf.setVideo(wpIS.isVideo());
						mf.save(EditPost.this);
					}
				}

				WordPress.currentPost = post;

			} else {

				if (curLocation == null) {
					latitude = post.getLatitude();
					longitude = post.getLongitude();
				}

				post.setTitle(title);
				// split up the post content if there's a more tag
				if (localDraft && content.indexOf(needle) >= 0) {
					post.setDescription(content.substring(0,
							content.indexOf(needle)));
					post.setMt_text_more(content.substring(
							content.indexOf(needle) + needle.length(),
							content.length()));
				} else {
					post.setDescription(content);
					post.setMt_text_more("");
				}
				post.setMediaPaths(images);
				post.setDate_created_gmt(pubDateTimestamp);
				post.setCategories(categories);
				post.setMt_keywords(tags);
				post.setPost_status(status);
				post.setWP_password(password);
				post.setLatitude(latitude);
				post.setLongitude(longitude);
				post.setWP_post_form(postFormat);
				success = post.update();
			}

		}

		return success;
	}

	@Override
	public boolean onKeyDown(int i, KeyEvent event) {

		// only intercept back button press
		if (i == KeyEvent.KEYCODE_BACK) {

			AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(
					EditPost.this);
			dialogBuilder
					.setTitle(getResources().getText(R.string.cancel_edit));
			dialogBuilder.setMessage(getResources().getText(
					(isPage) ? R.string.sure_to_cancel_edit_page
							: R.string.sure_to_cancel_edit));
			dialogBuilder.setPositiveButton(getResources()
					.getText(R.string.yes),
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog,
								int whichButton) {
							Bundle bundle = new Bundle();

							bundle.putString("returnStatus", "CANCEL");
							Intent mIntent = new Intent();
							mIntent.putExtras(bundle);
							setResult(RESULT_OK, mIntent);
							finish();
						}
					});
			dialogBuilder.setNegativeButton(
					getResources().getText(R.string.no),
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog,
								int whichButton) {
							// just close the dialog window
						}
					});
			dialogBuilder.setCancelable(true);
			dialogBuilder.create().show();
		}

		return false;
	}

	/** Register for the updates when Activity is in foreground */
	@Override
	protected void onResume() {
		super.onResume();

	}

	/** Stop the updates when Activity is paused */
	@Override
	protected void onPause() {
		super.onPause();
		if (locationHelper != null) {
			locationHelper.cancelTimer();
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (locationHelper != null) {
			locationHelper.cancelTimer();
		}
	}

	private class getAddressTask extends AsyncTask<Double, Void, String> {

		protected void onPostExecute(String result) {
			TextView map = (TextView) findViewById(R.id.locationText);
			map.setText(result);
		}

		@Override
		protected String doInBackground(Double... args) {
			Geocoder gcd = new Geocoder(EditPost.this, Locale.getDefault());
			String finalText = "";
			List<Address> addresses;
			try {
				addresses = gcd.getFromLocation(args[0], args[1], 1);
				String locality = "", adminArea = "", country = "";
				if (addresses.get(0).getLocality() != null)
					locality = addresses.get(0).getLocality();
				if (addresses.get(0).getAdminArea() != null)
					adminArea = addresses.get(0).getAdminArea();
				if (addresses.get(0).getCountryName() != null)
					country = addresses.get(0).getCountryName();

				if (addresses.size() > 0) {
					finalText = ((locality.equals("")) ? locality : locality
							+ ", ")
							+ ((adminArea.equals("")) ? adminArea : adminArea
									+ " ") + country;
					if (finalText.equals(""))
						finalText = getResources().getText(
								R.string.location_not_found).toString();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			return finalText;
		}
	}

	protected void setContent() {
		Intent intent = getIntent();
		String text = intent.getStringExtra(Intent.EXTRA_TEXT);
		String title = intent.getStringExtra(Intent.EXTRA_SUBJECT);
		if (text != null) {
			EditText titleET = (EditText) findViewById(R.id.title);

			if (title != null) {
				titleET.setText(title);
			}

			WPEditText contentET = (WPEditText) findViewById(R.id.postContent);
			// It's a youtube video link! need to strip some parameters so the
			// embed will work
			if (text.contains("youtube_gdata")) {
				text = text.replace("feature=youtube_gdata", "");
				text = text.replace("&", "");
				text = text.replace("_player", "");
				text = text.replace("watch?v=", "v/");
				text = "<object width=\"480\" height=\"385\"><param name=\"movie\" value=\""
						+ text
						+ "\"></param><param name=\"allowFullScreen\" value=\"true\"></param><param name=\"allowscriptaccess\" value=\"always\"></param><embed src=\""
						+ text
						+ "\" type=\"application/x-shockwave-flash\" allowscriptaccess=\"always\" allowfullscreen=\"true\" width=\"480\" height=\"385\"></embed></object>";
				contentET.setText(text);
			} else {
				// add link tag around URLs, trac #64
				text = text.replaceAll("((http|https|ftp|mailto):\\S+)",
						"<a href=\"$1\">$1</a>");
				contentET.setText(WPHtml.fromHtml(StringHelper.addPTags(text),
						EditPost.this, post));
			}
		} else {
			String action = intent.getAction();
			final String type = intent.getType();
			final ArrayList<Uri> multi_stream;
			if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
				multi_stream = intent
						.getParcelableArrayListExtra((Intent.EXTRA_STREAM));
			} else {
				multi_stream = new ArrayList<Uri>();
				multi_stream.add((Uri) intent
						.getParcelableExtra(Intent.EXTRA_STREAM));
			}

			Vector<Serializable> params = new Vector<Serializable>();
			params.add(multi_stream);
			params.add(type);
			new processAttachmentsTask().execute(params);
		}

	}

	private class processAttachmentsTask extends
			AsyncTask<Vector<?>, Void, SpannableStringBuilder> {

		protected void onPreExecute() {

			showDialog(ID_DIALOG_LOADING);
		}

		protected void onPostExecute(SpannableStringBuilder result) {
			dismissDialog(ID_DIALOG_LOADING);
			if (result != null) {
				if (result.length() > 0) {
					WPEditText postContent = (WPEditText) findViewById(R.id.postContent);
					postContent.setText(result);
				}
			}
		}

		@Override
		protected SpannableStringBuilder doInBackground(Vector<?>... args) {
			ArrayList<?> multi_stream = (ArrayList<?>) args[0].get(0);
			String type = (String) args[0].get(1);
			SpannableStringBuilder ssb = new SpannableStringBuilder();
			for (int i = 0; i < multi_stream.size(); i++) {
				Uri curStream = (Uri) multi_stream.get(i);
				if (curStream != null && type != null) {
					String imgPath = curStream.getEncodedPath();

					ssb = addMedia(imgPath, curStream, ssb);

				}
			}
			return ssb;
		}
	}

	public SpannableStringBuilder addMedia(String imgPath, Uri curStream,
			SpannableStringBuilder ssb) {
		Bitmap resizedBitmap = null;
		String imageTitle = "";

		ImageHelper ih = new ImageHelper();

		Display display = getWindowManager().getDefaultDisplay();
		int width = display.getWidth();

		HashMap<?, ?> mediaData = ih.getImageBytesForPath(imgPath,
				EditPost.this);

		if (mediaData == null) {
			return null;
		}

		byte[] finalBytes = ih.createThumbnail((byte[]) mediaData.get("bytes"),
				String.valueOf(width / 2),
				(String) mediaData.get("orientation"), true);

		resizedBitmap = BitmapFactory.decodeByteArray(finalBytes, 0,
				finalBytes.length);

		WPImageSpan is = new WPImageSpan(EditPost.this, resizedBitmap,
				curStream);
		is.setTitle(imageTitle);
		is.setImageSource(curStream);
		is.setVideo(imgPath.contains("video"));
		ssb.append(" ");
		ssb.setSpan(is, ssb.length() - 1, ssb.length(),
				Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		AlignmentSpan.Standard as = new AlignmentSpan.Standard(
				Layout.Alignment.ALIGN_CENTER);
		ssb.setSpan(as, ssb.length() - 1, ssb.length(),
				Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		ssb.append("\n");

		return ssb;

	}

	private String getCategoriesCSV() {
		String csv = "";
		if (categories.length() > 0) {
			for (int i = 0; i < categories.length(); i++) {
				try {
					csv += EscapeUtils.unescapeHtml(categories.getString(i))
							+ ",";
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
			csv = csv.substring(0, csv.length() - 1);
		}
		return csv;
	}

	private DatePickerDialog.OnDateSetListener mDateSetListener = new DatePickerDialog.OnDateSetListener() {

		public void onDateSet(DatePicker view, int year, int monthOfYear,
				int dayOfMonth) {
			mYear = year;
			mMonth = monthOfYear;
			mDay = dayOfMonth;

			showDialog(ID_DIALOG_TIME);

		}
	};

	private TimePickerDialog.OnTimeSetListener mTimeSetListener = new TimePickerDialog.OnTimeSetListener() {

		public void onTimeSet(TimePicker view, int hour, int minute) {
			mHour = hour;
			mMinute = minute;

			Date d = new Date(mYear - 1900, mMonth, mDay, mHour, mMinute);
			long timestamp = d.getTime();

			try {
				int flags = 0;
				flags |= android.text.format.DateUtils.FORMAT_SHOW_DATE;
				flags |= android.text.format.DateUtils.FORMAT_ABBREV_MONTH;
				flags |= android.text.format.DateUtils.FORMAT_SHOW_YEAR;
				flags |= android.text.format.DateUtils.FORMAT_SHOW_TIME;
				String formattedDate = DateUtils.formatDateTime(EditPost.this,
						timestamp, flags);
				customPubDate = timestamp;
				TextView tvPubDate = (TextView) findViewById(R.id.pubDate);
				tvPubDate.setText(formattedDate);
				isCustomPubDate = true;
			} catch (Exception e) {
				e.printStackTrace();
			}

		}

	};

}
