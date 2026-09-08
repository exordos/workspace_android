package ru.genesiscorporation.workspace.beta.sharefixture;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/** SDK-only test APK receiver: its process has no dependency on the target app's Kotlin runtime. */
public class ExternalShareReceiverActivity extends Activity {
    public static final String CALLBACK = "cassi.share.fixture.callback";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent incoming = getIntent();
        PendingIntent callback = incoming.getParcelableExtra(CALLBACK);
        Intent receipt = new Intent().putExtra("receiver_uid", Process.myUid());
        try {
            ArrayList<Uri> uris;
            if (Intent.ACTION_SEND_MULTIPLE.equals(incoming.getAction())) {
                uris = incoming.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                if (uris == null) uris = new ArrayList<>();
            } else {
                uris = new ArrayList<>();
                Uri single = incoming.getParcelableExtra(Intent.EXTRA_STREAM);
                if (single != null) uris.add(single);
            }
            receipt.putExtra("action", incoming.getAction());
            receipt.putExtra("text", incoming.getStringExtra(Intent.EXTRA_TEXT));
            receipt.putExtra("mime_type", incoming.getType());
            ArrayList<String> contents = new ArrayList<>();
            boolean canWrite = false;
            for (Uri uri : uris) {
                if (!"content".equals(uri.getScheme())) throw new IllegalArgumentException("Expected a content URI");
                try (InputStream stream = getContentResolver().openInputStream(uri)) {
                    if (stream == null) throw new IllegalStateException("Cannot read the granted attachment");
                    try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                        char[] buffer = new char[1024];
                        StringBuilder text = new StringBuilder();
                        int count;
                        while (text.length() < 1024 && (count = reader.read(buffer, 0, 1024 - text.length())) >= 0) {
                            text.append(buffer, 0, count);
                        }
                        contents.add(text.toString());
                    }
                }
                try (OutputStream stream = getContentResolver().openOutputStream(uri, "wa")) {
                    if (stream != null) canWrite = true;
                } catch (Exception expectedReadOnlyDenial) {
                    // A read-only grant must fail this separate-UID write attempt.
                }
            }
            receipt.putStringArrayListExtra("contents", contents);
            receipt.putExtra("can_write", canWrite);
        } catch (Exception failure) {
            receipt.putExtra("failure", failure.getClass().getSimpleName());
        }
        try {
            if (callback != null) callback.send(this, RESULT_OK, receipt);
        } catch (PendingIntent.CanceledException ignored) {
            // The originating test may already have timed out or been cancelled.
        } finally {
            finish();
        }
    }
}
