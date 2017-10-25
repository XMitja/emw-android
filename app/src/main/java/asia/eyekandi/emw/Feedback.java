package asia.eyekandi.emw;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.androidquery.AQuery;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import roboguice.util.Ln;

public class Feedback extends AppCompatActivity {
    private static final File storageDir = new File(Environment.getExternalStorageDirectory(), "logcat");
    private File tempFile;

    static {
        storageDir.mkdirs();
    }

    private AQuery aq;

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        final MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.feedbackmenu, menu);
        return true;
    }

    @Override
    public final boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                finish();
                //NavUtils.navigateUpFromSameTask(this);
                return true;
            case R.id.mnuReportBugSend:
                this.send();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feedback);

        try {
            Ln.w("BUGREPORT on MODEL %s API %d VERSION %s",
                    Build.MODEL,
                    Build.VERSION.SDK_INT,
                    getPackageManager().getPackageInfo(getPackageName(), 0).versionName
            );
        } catch (android.content.pm.PackageManager.NameNotFoundException ex) {
            Ln.e(ex, "BUGREPORT Package not found");
        }

        getSupportActionBar().setDisplayUseLogoEnabled(false);
        getSupportActionBar().setDisplayShowTitleEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        try {
            getSupportActionBar().setSubtitle(String.valueOf(getPackageManager().getPackageInfo(getPackageName(), 0).versionName));
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        //getSupportActionBar().setDisplayShowHomeEnabled(false);
        //getSupportActionBar().setDisplayUseLogoEnabled(false);

        aq = new AQuery(this);

        tempFile = new File(storageDir, "logcat.txt.gz");
        try {
            BufferedInputStream is = null;
            GZIPOutputStream out = null;
            try {
                byte[] buf = new byte[8096];
                List<String> args = new ArrayList<>(Arrays.asList("logcat", "-v", "time", "-d"));
                Process dumpLogcatProcess = exec(args);
                is = new BufferedInputStream(dumpLogcatProcess.getInputStream());
                out = new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(tempFile, false)));
                while (true) {
                    int nread = is.read(buf, 0, buf.length);
                    if (nread < 0)
                        break;
                    out.write(buf, 0, nread);
                }
                Ln.d("Wrote to file %s", tempFile);
            } finally {
                if (is != null)
                    is.close();
                if (out != null)
                    out.close();
            }
        } catch (java.io.IOException ex) {
            // show alert dialog
        }

    }

    private void send() {
        android.widget.EditText tv = aq.id(R.id.bugDescription).getEditText();
        final String message = tv.getText().toString();
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) tv.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(tv.getWindowToken(), 0);

        Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND);
        String aEmailList[] = {"mitja.sarp@gmail.com"};
        emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, aEmailList);
        emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "[EMW] Android Feedback");
        emailIntent.setType("message/rfc822");
        emailIntent.putExtra(android.content.Intent.EXTRA_TEXT, message);

        Uri uri = Uri.fromFile(tempFile);
        emailIntent.putExtra(Intent.EXTRA_STREAM, uri);
        startActivity(Intent.createChooser(emailIntent, "Send as E-Mail with:"));
    }

    private static Process exec(List<String> args) throws java.io.IOException {
        return Runtime.getRuntime().exec(Feedback.toArray(args, String.class));
    }

    private static <T> T[] toArray(List<T> list, Class<T> clazz) {
        @SuppressWarnings("unchecked")
        T[] result = (T[]) Array.newInstance(clazz, list.size());
        for (int i = 0; i < list.size(); i++) {
            result[i] = list.get(i);
        }
        return result;
    }
}
