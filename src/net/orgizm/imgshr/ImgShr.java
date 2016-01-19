package net.orgizm.imgshr;

import android.app.Activity;
import android.os.Bundle;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import java.net.URL;
import java.security.cert.X509Certificate;
import javax.net.ssl.HostnameVerifier;
import java.net.HttpURLConnection;
// import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class ImgShr extends Activity
{
	Intent intent;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		intent = getIntent();

		EditText input = (EditText) findViewById(R.id.slug);
		input.setText(getLastSlug(), TextView.BufferType.EDITABLE);
	}

	private String getFileName(ContentResolver cr, Uri uri) {
		String[] projection = {MediaStore.MediaColumns.DISPLAY_NAME};
		Cursor metaCursor = cr.query(uri, projection, null, null, null);
		String fileName = null;

		if (metaCursor != null) {
			try {
				if (metaCursor.moveToFirst()) {
					fileName = metaCursor.getString(0);
				}
 			}
			finally {
				metaCursor.close();
			}
		}

		return fileName;
	}

	private String getLastSlug() {
		SharedPreferences pref = getPreferences(Context.MODE_PRIVATE);
		return pref.getString("lastSlug", "");
	}

	private void setLastSlug(String slug) {
		SharedPreferences.Editor editor = getPreferences(Context.MODE_PRIVATE).edit();
		editor.putString("lastSlug", slug);
		editor.commit();
	}

	public void uploadImageCallback(View view) throws Exception {
		final TextView text = (TextView) findViewById(R.id.url);

		new Thread(new Runnable() {
			public void run() {
				runOnUiThread(new Runnable() {
					public void run() {
						text.setText("Uploading...");
					}
				});

				try {
					final String message = uploadImage();

					runOnUiThread(new Runnable() {
						public void run() {
							text.setText(message);
						}
					});
				}
				catch (Exception e) {
					Log.d("net.orgizm.imgshr", e.getMessage());
				}
			}
		}).start();
	}

	private String uploadImage() throws Exception {
		Uri imageUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);

		String slug = ((EditText) findViewById(R.id.slug)).getText().toString();
		setLastSlug(slug);

		// String url = "https://imgshr.orgizm.net/api/!" + slug;
		String url = "http://10.0.2.2:3000/api/!" + slug;

		String message = null;

		if (imageUri != null) {
			ContentResolver cr = getContentResolver();
			InputStream file = cr.openInputStream(imageUri);

			/*
			// Create a trust manager that does not validate certificate chains
			TrustManager[] trustAllCerts = new TrustManager[] {
				new X509TrustManager() {
					public java.security.cert.X509Certificate[] getAcceptedIssuers() {
						return null;
					}

					public void checkClientTrusted(X509Certificate[] certs, String authType) {
					}

					public void checkServerTrusted(X509Certificate[] certs, String authType) {
					}
				}
			};
	 
			// Install the all-trusting trust manager
			SSLContext sc = SSLContext.getInstance("SSL");
			sc.init(null, trustAllCerts, new java.security.SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
	 
			// Create all-trusting host name verifier
			HostnameVerifier allHostsValid = new HostnameVerifier() {
				public boolean verify(String hostname, SSLSession session) {
					return true;
				}
			};
	 
			// Install the all-trusting host verifier
			HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
			*/

			String param    = "picture[image][]";
			String filename = getFileName(cr, imageUri);
			String boundary = "*****";
			String crlf     = "\r\n";
			String cd       = "Content-Disposition: form-data; name=\"" + param + "\"; filename=\"" + filename + "\"" + crlf;
			String ct       = "Content-Type: " + cr.getType(imageUri) + crlf;

			// HttpsURLConnection conn = (HttpsURLConnection) (new URL(url)).openConnection();
			HttpURLConnection conn = (HttpURLConnection) (new URL(url)).openConnection();

			try {
				conn.setDoOutput(true);
				conn.setChunkedStreamingMode(0);

				conn.setRequestMethod("POST");
				conn.setRequestProperty("Connection", "Keep-Alive");
				conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);

				OutputStream out = new BufferedOutputStream(conn.getOutputStream());

				out.write(("--" + boundary + crlf).getBytes());
				out.write(cd.getBytes());
				out.write(ct.getBytes());
				out.write(crlf.getBytes());

				byte[] buffer = new byte[256];
				int bytesRead = 0;
				while ((bytesRead = file.read(buffer)) != -1) {
					out.write(buffer, 0, bytesRead);
				}

				out.write(crlf.getBytes());
				out.write(("--" + boundary + "--" + crlf).getBytes());

				out.flush();

				int responseCode = 0;
				responseCode = conn.getResponseCode();
				String responseMessage = conn.getResponseMessage();

				Log.i("net.orgizm.imgshr", "HTTP Response: " + responseCode + " " + responseMessage);

				message = responseCode + " " + responseMessage;
			}
			finally {
				conn.disconnect();
			}
		}

		return message;
	}
}
