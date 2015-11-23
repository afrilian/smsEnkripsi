package com.rcythr.masq;

import com.herupurwito.com.seip.R;
import com.rcythr.masq.keymanagement.Key;
import com.rcythr.masq.keymanagement.KeyManager;
import com.rcythr.masq.util.AES;
import com.rcythr.masq.SMSActivity.SMSListAdapter.Message;

import android.os.Bundle;
import java.text.DateFormat;
import java.util.Date;

import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.util.encoders.Base64;

import android.app.ListActivity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

public class Inbox extends ListActivity {

	String[] Inbox_name = new String[100], Inbox_number = new String[100],
			Inbox_date = new String[100], Inbox_type = new String[100],
			Inbox_msg = new String[100];

	int pos = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		Inbox_Read();
		setListAdapter(new InboxArrayAdapter(this, Inbox_name, Inbox_number,
				Inbox_date, Inbox_type, Inbox_msg));

	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) { // TODO
																				// Auto-generated
																				// method
																				// stub
		super.onListItemClick(l, v, position, id);
		Toast.makeText(Inbox.this, "Number : " + Inbox_number[position],
				Toast.LENGTH_SHORT).show();
		Intent intent = new Intent(this, ViewMesg.class);
		intent.putExtra("name", Inbox_name[position]);
		intent.putExtra("no", Inbox_number[position]);
		intent.putExtra("date", Inbox_date[position]);
		intent.putExtra("time", Inbox_type[position]);
		intent.putExtra("msg", Inbox_msg[position]);
		startActivity(intent);
	}

	void Inbox_Read() {
		Uri mSmsinboxQueryUri = Uri.parse("content://sms/inbox");
		Cursor cursor1 = getContentResolver().query(
				mSmsinboxQueryUri,
				new String[] { "_id", "thread_id", "address", "person", "date",
						"body", "type" }, null, null, null);
		startManagingCursor(cursor1);
		String[] columns = new String[] { "address", "person", "date", "body",
				"type" };

		if (cursor1.getCount() > 0) {
			// String count = Integer.toString(cursor1.getCount());

			while (cursor1.moveToNext()) {

				String msg = cursor1.getString(cursor1
						.getColumnIndex(columns[3]));

				String nomorPengirim = cursor1.getString(cursor1
						.getColumnIndex(columns[0]));

				boolean prefixNumber = nomorPengirim.startsWith("+62");
				boolean beginsWithUnderscore = msg.startsWith("_");

				if (prefixNumber) {
					String pecah[] = nomorPengirim.split("\\+62");
					String number = "0" + pecah[1];
					if (beginsWithUnderscore) {

						String name = cursor1.getString(cursor1
								.getColumnIndex(columns[1]));
						String date = cursor1.getString(cursor1
								.getColumnIndex(columns[2]));
						String type = cursor1.getString(cursor1
								.getColumnIndex(columns[4]));

						Inbox_name[pos] = name;
						Inbox_number[pos] = number;

						if (date != null) {
							long l = Long.parseLong(date);
							Date d = new Date(l);
							Inbox_date[pos] = DateFormat.getDateInstance(
									DateFormat.LONG).format(d);
							Inbox_type[pos] = DateFormat.getTimeInstance()
									.format(d);
						} else {
							Inbox_date[pos] = date;
							Inbox_type[pos] = type;
						}

						Key key = KeyManager.getInstance().getLookup()
								.get(number);
						String pruned = msg.substring(1);
						byte[] clearText = null;
						if (key.key != null) {
							try {
								clearText = AES.handle(false,
										Base64.decode(pruned), key.key);
							} catch (InvalidCipherTextException e) {
							}

						}
						String isipesan = new String(clearText);
						Inbox_msg[pos] = isipesan;

					}
				} else {
					String number = nomorPengirim;
					if (beginsWithUnderscore) {

						String name = cursor1.getString(cursor1
								.getColumnIndex(columns[1]));
						String date = cursor1.getString(cursor1
								.getColumnIndex(columns[2]));
						String type = cursor1.getString(cursor1
								.getColumnIndex(columns[4]));

						Inbox_name[pos] = name;
						Inbox_number[pos] = number;

						if (date != null) {
							long l = Long.parseLong(date);
							Date d = new Date(l);
							Inbox_date[pos] = DateFormat.getDateInstance(
									DateFormat.LONG).format(d);
							Inbox_type[pos] = DateFormat.getTimeInstance()
									.format(d);
						} else {
							Inbox_date[pos] = date;
							Inbox_type[pos] = type;
						}

						Key key = KeyManager.getInstance().getLookup()
								.get(number);
						String pruned = msg.substring(1);
						byte[] clearText = null;
						if (key.key != null) {
							try {
								clearText = AES.handle(false,
										Base64.decode(pruned), key.key);
							} catch (InvalidCipherTextException e) {
							}
							String isipesan = new String(clearText);
							Inbox_msg[pos] = isipesan;

						} else {
							Inbox_msg[pos] = msg;
						}

					}
				}
				pos += 1;
			}
		}
	}

}