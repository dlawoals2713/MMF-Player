package com.dlawoals2713.yamaha;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.View;
import android.view.Choreographer;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.yamaha.smafsynth.m7.emu.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.dlyt.yanndroid.oneui.layout.ToolbarLayout;

public class MMFPlayerActivity extends AppCompatActivity implements View.OnClickListener,
        SeekBar.OnSeekBarChangeListener, EmuSmw7.ChannelDataListener {
    private static final int PICK_MMF_FILE = 1;

    private ImageButton initButton;
    private ImageButton playButton;
    private ImageButton stopButton;
    private ImageButton releaseButton;
    private TextView fileNameTextView;
    private TextView timeTextView;
    private SeekBar progressSeekBar;
    private SeekBar volumeSeekBar;
    private LinearLayout content;

    private TextView titleTextView;
    private TextView artistTextView;
    private TextView copyrightTextView;
    private TextView genreTextView;
    private TextView miscTextView;

    private EmuSmw7 emuSmw7;
    private int sampleRate = 22050;
    private byte[] mmfData;
    private String currentFileName;
    public DataParsers dataParser;

    private ToolbarLayout toolbarLayout;
    private SharedPreferences sp;

    private Choreographer.FrameCallback frameCallback;
    private boolean isUpdating = false;
    private Context context;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Intent intent = new Intent();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mmf_player);

        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                },
                1);

        sp = getSharedPreferences("setting", Activity.MODE_PRIVATE);
        currentFileName = getString(R.string.file_unselected);
        initUI();
        emuSmw7 = new EmuSmw7();
        emuSmw7.EmuSmw7Init(); // EmuSmw7 초기화
        emuSmw7.setChannelDataListener(this); // 채널 데이터 리스너 설정
        context = this;

        Uri fileUri = getIntent().getData();
        if (fileUri != null) {
            try {
                currentFileName = getFileNameFromUri(fileUri);
                fileNameTextView.setText(currentFileName);

                InputStream inputStream = getContentResolver().openInputStream(fileUri);
                if (inputStream != null) {
                    mmfData = new byte[inputStream.available()];
                    inputStream.read(mmfData);
                    inputStream.close();

                    parseAndDisplayMetadata();

                    Toast.makeText(this, getString(R.string.file_loaded, currentFileName), Toast.LENGTH_SHORT).show();
                    updateButtonStates();
                    Log.d("MMFPlayer", "파일 URI: " + fileUri.toString());
                }
            } catch (Exception e) {
                Toast.makeText(this, getString(R.string.file_loadError, e.getMessage()), Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        } else {
            Log.d("MMFPlayer", "Error: File URI is null");
        }
    }

    private void initUI() {
        content = findViewById(R.id.content);
        content.setClipChildren(false);
        content.setClipToPadding(false);

        sampleRate = Integer.parseInt(sp.getString("sr", "22050"));

        fileNameTextView = findViewById(R.id.text_file_name);
        fileNameTextView.setText(currentFileName);

        titleTextView = findViewById(R.id.text_title);
        artistTextView = findViewById(R.id.text_artist);
        copyrightTextView = findViewById(R.id.text_copyright);
        genreTextView = findViewById(R.id.text_genre);
        miscTextView = findViewById(R.id.text_misc);

        initButton = findViewById(R.id.button_init);
        playButton = findViewById(R.id.button_play);
        stopButton = findViewById(R.id.button_stop);
        releaseButton = findViewById(R.id.button_release);

        initButton.setOnClickListener(this);
        playButton.setOnClickListener(this);
        stopButton.setOnClickListener(this);
        releaseButton.setOnClickListener(this);

        progressSeekBar = findViewById(R.id.seekbar_progress);
        progressSeekBar.setEnabled(false);
        volumeSeekBar = findViewById(R.id.seekbar_volume);
        volumeSeekBar.setMax(127);
        volumeSeekBar.setProgress(100);
        volumeSeekBar.setOnSeekBarChangeListener(this);

        timeTextView = findViewById(R.id.text_time);

        toolbarLayout = findViewById(R.id.toolbar_view);
        toolbarLayout.inflateToolbarMenu(R.menu.player);
        toolbarLayout.getToolbarMenu().findItem(R.id.settings).setTitle(getString(R.string.setting));
        toolbarLayout.setOnToolbarMenuItemClickListener(item -> {
            int itemId = item.getItemId();  // item ID를 미리 저장해두기

            if (itemId == R.id.settings) {
                setting();
            } else if (itemId == R.id.open) {
                open();
            }

            return true;
        });
        updateButtonStates();
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();

        if (id == R.id.button_init) {
            String rateText = String.valueOf(sampleRate).trim();
            sp.edit().putString("sr", rateText).apply();

            try {
                sampleRate = Integer.parseInt(rateText);
                if (sampleRate < 11025) sampleRate = 11025;
                if (sampleRate > 48000) sampleRate = 48000;

                // 기존 리소스 정리
                if (emuSmw7 != null) {
                    emuSmw7.releaseAudioResources();
                }

                long result = emuSmw7.startAudio(sampleRate);

                runOnUiThread(() -> {
                    if (result >= 0) {
                        updateButtonStates();
                        Toast.makeText(MMFPlayerActivity.this, getString(R.string.init_success), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MMFPlayerActivity.this, getString(R.string.init_fail), Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (NumberFormatException e) {
                Toast.makeText(this, getString(R.string.init_error), Toast.LENGTH_SHORT).show();
            }
        } else if (id == R.id.button_play) {
            if (mmfData == null) {
                Toast.makeText(this, getString(R.string.play_unselected), Toast.LENGTH_SHORT).show();
                return;
            }

            parseAndDisplayMetadata();
            emuSmw7.startPlayback(mmfData, 100L, -1L, 15L, 32, 32);
            emuSmw7.setPlaybackVolume(volumeSeekBar.getProgress());

            startPlaybackUpdates();
            updateButtonStates();

        } else if (id == R.id.button_stop) {
            emuSmw7.stopPlayback();
            stopPlaybackUpdates();
            updateButtonStates();

        } else if (id == R.id.button_release) {
            emuSmw7.releaseAudioResources();
            stopPlaybackUpdates();
            updateButtonStates();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_MMF_FILE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                try {
                    InputStream inputStream = getContentResolver().openInputStream(uri);
                    if (inputStream != null) {
                        mmfData = new byte[inputStream.available()];
                        inputStream.read(mmfData);
                        inputStream.close();

                        currentFileName = getFileName(uri);
                        fileNameTextView.setText(currentFileName);

                        parseAndDisplayMetadata();

                        Toast.makeText(this, getString(R.string.file_loaded, currentFileName), Toast.LENGTH_SHORT).show();
                        updateButtonStates();
                    }
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    Toast.makeText(this, getString(R.string.file_notFound), Toast.LENGTH_SHORT).show();
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(this, getString(R.string.file_error), Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void parseAndDisplayMetadata() {
        if (mmfData != null) {
            try {
                dataParser = new DataParsers(mmfData);
                updateMetadataDisplay();
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, getString(R.string.parse_error), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateMetadataDisplay() {
        if (dataParser != null) {
            titleTextView.setText(getString(R.string.title, (dataParser.getTitle().isEmpty() ? getString(R.string.data_unknown) : dataParser.getTitle())));
            artistTextView.setText(getString(R.string.artist, (dataParser.getArtistName().isEmpty() ? getString(R.string.data_unknown) : dataParser.getArtistName())));
            copyrightTextView.setText(getString(R.string.copyright, (dataParser.getCopyrightInfo().isEmpty() ? getString(R.string.data_unknown) : dataParser.getCopyrightInfo())));
            genreTextView.setText(getString(R.string.genre, (dataParser.getGenre().isEmpty() ? getString(R.string.data_unknown) : dataParser.getGenre())));
            miscTextView.setText(getString(R.string.etc, (dataParser.getMiscInfo().isEmpty() ? getString(R.string.data_unknown) : dataParser.getMiscInfo())));
        } else {
            titleTextView.setText(getString(R.string.title, getString(R.string.data_unknown)));
            artistTextView.setText(getString(R.string.artist, getString(R.string.data_unknown)));
            copyrightTextView.setText(getString(R.string.copyright, getString(R.string.data_unknown)));
            genreTextView.setText(getString(R.string.genre, getString(R.string.data_unknown)));
            miscTextView.setText(getString(R.string.etc, getString(R.string.data_unknown)));
        }
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    private void updateButtonStates() {
        if (emuSmw7 == null) {
            int cardBackground = getResources().getColor(R.color.cardBackground);
            int primaryColor = getResources().getColor(R.color.primaryColor);
            resetButtons(cardBackground);
            initButton.setEnabled(true);
            initButton.setImageTintList(ColorStateList.valueOf(primaryColor));
            return;
        }

        long state = emuSmw7.getPlaybackStatus();

        int primaryColor = getResources().getColor(R.color.primaryColor);
        int cardBackground = getResources().getColor(R.color.cardBackground);
        int errorColor = getResources().getColor(R.color.errorColor);

        resetButtons(cardBackground);

        switch ((int) state) {
            case 0: // 초기화되지 않음
                initButton.setEnabled(true);
                initButton.setImageTintList(ColorStateList.valueOf(primaryColor));
                break;
            case 1: // 초기화됨 (재생 준비 상태)
                if (mmfData != null) {
                    playButton.setEnabled(true);
                    playButton.setImageTintList(ColorStateList.valueOf(primaryColor));
                }
                releaseButton.setEnabled(true);
                releaseButton.setImageTintList(ColorStateList.valueOf(errorColor));
                break;
            case 2: // 재생 중
                stopButton.setEnabled(true);
                stopButton.setImageTintList(ColorStateList.valueOf(primaryColor));
                releaseButton.setEnabled(true);
                releaseButton.setImageTintList(ColorStateList.valueOf(errorColor));
                break;
        }
    }

    private void resetButtons(int defaultColor) {
        ColorStateList defaultTint = ColorStateList.valueOf(defaultColor);

        initButton.setEnabled(false);
        initButton.setImageTintList(defaultTint);

        playButton.setEnabled(false);
        playButton.setImageTintList(defaultTint);

        stopButton.setEnabled(false);
        stopButton.setImageTintList(defaultTint);

        releaseButton.setEnabled(false);
        releaseButton.setImageTintList(defaultTint);
    }

    private void startPlaybackUpdates() {
        stopPlaybackUpdates();
        isUpdating = true;

        frameCallback = new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                if (emuSmw7 != null && isUpdating) {
                    long position = emuSmw7.getCurrentPlaybackPosition();
                    long length = emuSmw7.getAudioLength();

                    if (length > 0) {
                        progressSeekBar.setMax((int) length);
                        progressSeekBar.setProgress((int) position);

                        timeTextView.setText(
                                formatTime(position) + " / " + formatTime(length));
                    }
                }

                if (isUpdating) {
                    Choreographer.getInstance().postFrameCallback(this);
                }
            }
        };

        Choreographer.getInstance().postFrameCallback(frameCallback);
    }

    private void stopPlaybackUpdates() {
        isUpdating = false;
        if (frameCallback != null) {
            Choreographer.getInstance().removeFrameCallback(frameCallback);
            frameCallback = null;
        }
    }

    private String formatTime(long milliseconds) {
        int seconds = (int) (milliseconds / 1000);
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (seekBar == volumeSeekBar && emuSmw7 != null) {
            emuSmw7.setPlaybackVolume(progress);
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {}

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {}

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (emuSmw7 != null) {
            emuSmw7.stopPlayback();
            emuSmw7.releaseAudioResources();
        }
        stopPlaybackUpdates();

        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }

    private void setting() {
        intent.setClass(getApplicationContext(), SettingActivity.class);
        startActivity(intent);
    }

    private void open() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_MMF_FILE);
    }

    @Override
    public void onResume() {
        super.onResume();
        sampleRate = Integer.parseInt(sp.getString("sr", "22050"));
    }

    private String getFileNameFromUri(Uri uri) {
        String displayName = "";
        try (Cursor cursor = getContentResolver().query(
                uri,
                new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                displayName = cursor.getString(0);
            }
        } catch (Exception e) {
            e.printStackTrace();
            displayName = uri.getLastPathSegment();
        }
        return displayName;
    }

    // ChannelDataListener 구현
    @Override
    public void onChannelDataReady(int leftChannelValue, int rightChannelValue) {
        // 여기에 오디오 채널 데이터를 처리하는 코드를 추가할 수 있습니다.
        // 예: 시각화 업데이트 등
        // Log.d("ChannelData", "Left: " + leftChannelValue + ", Right: " + rightChannelValue);
    }
}