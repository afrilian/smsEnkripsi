/**	This file is part of Masq.

    Masq is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Masq is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Masq.  If not, see <http://www.gnu.org/licenses/>.
 **/

package com.rcythr.masq;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;

import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.util.encoders.Base64;

import com.herupurwito.com.seip.R;
import com.rcythr.masq.keymanagement.Contact;
import com.rcythr.masq.keymanagement.Key;
import com.rcythr.masq.keymanagement.KeyManager;
import com.rcythr.masq.util.AES;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.text.ClipboardManager;

import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

@SuppressWarnings("deprecation")
public class SMSActivity extends ListActivity {

	private static final int TYPE_RECV = 1;
	private static final int TYPE_SENT = 2;

	private EditText text;
	private String displayName;
	private String currentAddress;
	private SMSListAdapter smsAdapter;
	private ContactSpinnerAdapter contactAdapter;
	private BroadcastReceiver receiver;

	/**
	 * Sends message to phoneNumber splitting the messages at the 160 character
	 * limit.
	 * 
	 * The message is then added to the SMS Messaging application's database.
	 * 
	 * @param phoneNumber
	 *            the number to send to
	 * @param message
	 *            the message to send
	 */
	private void sendSMS(String phoneNumber, String message) {
		// Split the message as appropriate.
		int cursor = 0;
		ArrayList<String> pieces = new ArrayList<String>();
		while (cursor < message.length()) {
			if (cursor + 158 < message.length() - 1) {
				if (pieces.size() > 0) {
					pieces.add("-" + message.substring(cursor, cursor + 158)
							+ "-");
				} else {
					pieces.add("_" + message.substring(cursor, cursor + 158)
							+ "-");
				}
			} else {
				if (pieces.size() > 0) {
					pieces.add("-" + message.substring(cursor));
				} else {
					pieces.add("_" + message.substring(cursor));
				}

			}
			cursor += 158;
		}

		SmsManager sms = SmsManager.getDefault();
		for (String piece : pieces) {
			// Send the message
			sms.sendTextMessage(phoneNumber, null, piece, null, null);

			// Add it to the database
			ContentValues values = new ContentValues();
			values.put("address", phoneNumber);
			values.put("type", TYPE_SENT);
			values.put("body", piece);
			values.put("read", true);
			values.put("seen", true);
			getContentResolver()
					.insert(Uri.parse("content://sms/sent"), values);
		}
	}

	/**
	 * Places a received message into the SMS Messaging application's database.
	 * 
	 * @param message
	 *            the message
	 */
	private void receiveSMS(SmsMessage message) {
		ContentValues values = new ContentValues();
		values.put("address", message.getOriginatingAddress());
		values.put("type", TYPE_RECV);
		values.put("body", message.getDisplayMessageBody());
		values.put("read", true);
		values.put("seen", true);
		getContentResolver().insert(Uri.parse("content://sms/inbox"), values);
	}

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		try {
			KeyManager.getInstance().init(this);
		} catch (Exception e) {
			Toast.makeText(this, R.string.unknown_error, Toast.LENGTH_LONG);
		}

		setContentView(R.layout.sms_main);

		// Setup the List View
		ListView view = getListView();
		registerForContextMenu(view);
		smsAdapter = new SMSListAdapter(this);
		view.setAdapter(smsAdapter);

		final Button send = (Button) findViewById(R.id.send);

		// Setup the person spinner
		Spinner personSpinner = (Spinner) findViewById(R.id.personSpinner);
		this.contactAdapter = new ContactSpinnerAdapter(this);
		personSpinner.setAdapter(contactAdapter);
		personSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {

			public void onItemSelected(AdapterView<?> arg0, View view,
					int arg2, long arg3) {
				TextView displayName = (TextView) view
						.findViewById(R.id.display_name);
				TextView address = (TextView) view.findViewById(R.id.address);

				Spinner personSpinner = (Spinner) findViewById(R.id.personSpinner);
				getPreferences(MODE_PRIVATE)
						.edit()
						.putString(
								"currentSelectedAddress",
								((Contact) contactAdapter.getItem(personSpinner
										.getSelectedItemPosition())).address)
						.commit();

				getListView().setSelection(getListView().getCount() - 1);

				SMSActivity.this.displayName = displayName.getText().toString();
				SMSActivity.this.currentAddress = address.getText().toString();
				smsAdapter.populateSMS(SMSActivity.this.displayName,
						currentAddress);

				text.setEnabled(true);
				send.setEnabled(true);
			}

			public void onNothingSelected(AdapterView<?> arg0) {
			}
		});

		text = (EditText) findViewById(R.id.smsBox);

		if (savedInstanceState != null) {
			text.setText(savedInstanceState.getString("textField"));
		}

		send.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				String message = text.getText().toString();
				if (message.length() > 0) {
					text.setText("");

					// Get the proper key
					Key key = KeyManager.getInstance().getLookup()
							.get(currentAddress);

					try {

						byte[] clearText = message.getBytes();

						sendSMS(currentAddress,
								new String(Base64.encode(AES.handle(true,
										clearText, key.key))));

						smsAdapter.populateSMS(displayName, currentAddress);

					} catch (InvalidCipherTextException e) {
						e.printStackTrace();
						Toast.makeText(SMSActivity.this, R.string.error_send,
								Toast.LENGTH_SHORT).show();
					}
				}
			}
		});

		contactAdapter.populate();
		int savedPosition = contactAdapter.getPosition(getPreferences(
				MODE_PRIVATE).getString("currentSelectedAddress", ""));
		if (savedPosition != -1) {
			personSpinner.setSelection(savedPosition);
		}

		receiver = new SMSBroadcastReceiver();
	}

	@Override
	public void onResume() {
		IntentFilter filter = new IntentFilter();
		filter.addAction("android.provider.Telephony.SMS_RECEIVED");
		filter.setPriority(2);
		registerReceiver(receiver, filter);

		if (currentAddress != null && displayName != null) {
			smsAdapter.populateSMS(displayName, currentAddress);
		}

		super.onResume();
	}

	@Override
	public void onPause() {
		unregisterReceiver(receiver);

		super.onPause();
	}

	@Override
	public void onSaveInstanceState(Bundle bundle) {
		bundle.putString("textField", text.getText().toString());
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.layout.sms_context_menu, menu);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		final SMSListAdapter.Message msg;
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
				.getMenuInfo();
		switch (item.getItemId()) {
		case R.id.copy:
			msg = (SMSListAdapter.Message) smsAdapter.getItem(info.position);

			ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
			clipboard.setText(msg.message);

			return true;
		case R.id.edit:
			msg = (SMSListAdapter.Message) smsAdapter.getItem(info.position);

			AlertDialog.Builder alert = new AlertDialog.Builder(this);

			alert.setTitle(R.string.edit);
			alert.setMessage(R.string.edit_explain);

			// Set an EditText view to get user input
			final EditText input = new EditText(this);
			alert.setView(input);
			input.setText(msg.message);

			alert.setPositiveButton(R.string.save,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog,
								int whichButton) {
							msg.edit(input.getText().toString());
						}
					});

			alert.show();

			// msg.edit(newMessage)
			return true;
		case R.id.delete:
			msg = (SMSListAdapter.Message) smsAdapter.getItem(info.position);
			msg.delete();
			return true;
		default:
			return super.onContextItemSelected(item);
		}
	}

	public class SMSListAdapter extends BaseAdapter {

		public class Message {
			public long id = -1;
			public String sender;
			public String message;

			public void edit(String newMessage) {
				ContentValues values = new ContentValues();
				values.put("body", newMessage);

				ContentResolver contentResolver = context.getContentResolver();
				contentResolver.update(Uri.parse("content://sms"), values,
						"_id = " + Long.toString(id), null);
				message = newMessage;
				notifyDataSetChanged();
			}

			public void delete() {
				ContentResolver contentResolver = context.getContentResolver();
				contentResolver.delete(Uri.parse("content://sms"), "_id = "
						+ Long.toString(id), null);

				elements.remove(this);
				notifyDataSetChanged();
			}
		}

		private Activity context;

		List<Message> elements = new ArrayList<Message>();

		public SMSListAdapter(Activity context) {
			this.context = context;
		}

		public void populateSMS(String contactName, String contactAddress) {

			ContentResolver contentResolver = context.getContentResolver();
			Cursor cursor = contentResolver.query(Uri.parse("content://sms"),
					new String[] { "_id", "body", "type" }, "address = "
							+ contactAddress, null, "date DESC");

			elements.clear();
			if (cursor.moveToLast()) {

				Message safeKeepingSend = null;
				Message safeKeepingRecv = null;

				String message;
				long id;

				do {
					Message temp;
					id = cursor.getLong(0);
					message = cursor.getString(1);

					boolean beginsWithUnderscore = message.startsWith("_");
					boolean beginsWithHypen = message.startsWith("-")
							&& message.length() > 1;
					boolean endsWithHypen = message.endsWith("-");

					if (beginsWithUnderscore && endsWithHypen) {
						// Needs to have at least one concat

						temp = new Message();
						temp.id = id;
						temp.message = message.substring(1,
								message.length() - 1);

						if (cursor.getInt(2) == TYPE_RECV) {
							temp.sender = contactName;
							safeKeepingRecv = temp;
						} else {
							temp.sender = context.getString(R.string.me);
							safeKeepingSend = temp;
						}
						continue;

					} else if (beginsWithUnderscore) {
						// Can be decrypt now

						temp = new Message();
						temp.id = id;
						temp.message = message;

						if (cursor.getInt(2) == TYPE_RECV) {
							temp.sender = contactName;
						} else {
							temp.sender = context.getString(R.string.me);
						}

						Key key = KeyManager.getInstance().getLookup()
								.get(currentAddress);
						if (key.key != null) {
							try {
								String pruned = temp.message.substring(1);
								byte[] clearText = AES.handle(false,
										Base64.decode(pruned), key.key);
								temp.message = new String(clearText);
							} catch (Exception e) {
								// Not encrypted. Use cleartext
							}
						}

						elements.add(temp);
						continue;

					} else if (beginsWithHypen && endsWithHypen) {
						// Needs at least one more concat

						if (cursor.getInt(2) == TYPE_RECV) {
							if (safeKeepingRecv != null) {
								safeKeepingRecv.message = safeKeepingRecv.message
										.concat(message.substring(1,
												message.length() - 1));
								continue;
							}
						} else {
							if (safeKeepingSend != null) {
								safeKeepingSend.message = safeKeepingSend.message
										.concat(message.substring(1,
												message.length() - 1));
								continue;
							}
						}

					} else if (beginsWithHypen) {
						// Safe keeper can be decrypt

						if (cursor.getInt(2) == TYPE_RECV) {
							if (safeKeepingRecv != null) {
								safeKeepingRecv.message = safeKeepingRecv.message
										.concat(message.substring(1));
								temp = safeKeepingRecv;
								safeKeepingSend = null;
							} else {
								temp = new Message();
								temp.id = id;
								temp.message = message;

								if (cursor.getInt(2) == TYPE_RECV) {
									temp.sender = contactName;
								} else {
									temp.sender = context
											.getString(R.string.me);
								}

								elements.add(temp);
								continue;
							}
						} else {
							if (safeKeepingSend != null) {
								safeKeepingSend.message = safeKeepingSend.message
										.concat(message.substring(1));
								temp = safeKeepingSend;
								safeKeepingSend = null;
							} else {
								temp = new Message();
								temp.id = id;
								temp.message = message;

								if (cursor.getInt(2) == TYPE_RECV) {
									temp.sender = contactName;
								} else {
									temp.sender = context
											.getString(R.string.me);
								}

								elements.add(temp);
								continue;
							}
						}

						Key key = KeyManager.getInstance().getLookup()
								.get(currentAddress);
						if (key.key != null) {
							try {
								byte[] clearText = AES.handle(false,
										Base64.decode(temp.message), key.key);
								temp.message = new String(clearText);
							} catch (Exception e) {
								// Not encrypted. Use cleartext
							}
						}

						elements.add(temp);
						continue;
					}

					temp = new Message();
					temp.id = id;
					temp.message = message;

					if (cursor.getInt(2) == TYPE_RECV) {
						temp.sender = contactName;
					} else {
						temp.sender = context.getString(R.string.me);
					}

					elements.add(temp);
				} while (cursor.moveToPrevious());

				cursor.close();
			}

			notifyDataSetChanged();
		}

		public int getCount() {
			return elements.size();
		}

		public Object getItem(int position) {
			return elements.get(position);
		}

		public long getItemId(int position) {
			return position;
		}

		public View getView(int position, View view, ViewGroup parent) {
			//if (view == null) {
				// LayoutInflater inflater = context.getLayoutInflater();
				LayoutInflater inflater = (LayoutInflater) context
						.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				view = inflater.inflate(R.layout.sms_row, parent, false);
			//}

			TextView sender = (TextView) view.findViewById(R.id.sender);
			TextView message = (TextView) view.findViewById(R.id.message);

			Message msg = elements.get(position);
			sender.setText(msg.sender);
			message.setText(msg.message);

			return view;
		}

	}

	private class ContactSpinnerAdapter extends BaseAdapter {

		ArrayList<Contact> contacts = new ArrayList<Contact>();
		Activity context;

		public ContactSpinnerAdapter(Activity context) {
			this.context = context;
		}

		public void populate() {
			contacts.clear();

			for (Entry<String, Key> entry : KeyManager.getInstance()
					.getLookup().entrySet()) {
				Contact c = new Contact();
				Key value = entry.getValue();

				if (value != null && value.key != null) {
					c.address = entry.getKey();
					c.name = value.displayName;

					contacts.add(c);
				}
			}

			java.util.Collections.sort(contacts, new Comparator<Contact>() {

				public int compare(Contact contact1, Contact contact2) {
					return contact1.name.compareTo(contact2.name);
				}

			});

			this.notifyDataSetChanged();
		}

		public int getCount() {
			return contacts.size();
		}

		public Object getItem(int position) {
			return contacts.get(position);
		}

		public long getItemId(int position) {
			return position;
		}

		public int getPosition(String address) {
			for (int i = 0; i < contacts.size(); ++i) {
				if (contacts.get(i).address.equals(address)) {
					return i;
				}
			}
			return -1;
		}

		public View getView(int position, View view, ViewGroup parent) {
			if (view == null) {
				LayoutInflater inflater = context.getLayoutInflater();
				view = inflater.inflate(R.layout.sms_spinner_row, null);
			}

			TextView displayName = (TextView) view
					.findViewById(R.id.display_name);
			TextView address = (TextView) view.findViewById(R.id.address);

			Contact contact = contacts.get(position);
			displayName.setText(contact.name);
			address.setText(contact.address);

			return view;
		}

	}

	public class SMSBroadcastReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(
					"android.provider.Telephony.SMS_RECEIVED")) {
				Bundle bundle = intent.getExtras();
				if (bundle != null) {
					Object[] pdus = (Object[]) bundle.get("pdus");
					for (int i = 0; i < pdus.length; i++) {
						SmsMessage message = SmsMessage
								.createFromPdu((byte[]) pdus[i]);
						if (message.getDisplayOriginatingAddress().equals(
								SMSActivity.this.currentAddress)) {
							receiveSMS(message);
							SMSActivity.this.smsAdapter.populateSMS(
									SMSActivity.this.displayName,
									SMSActivity.this.currentAddress);
							abortBroadcast();
						}
					}
				}
			}
		}

	}

}
