package ycj.com.vediotest;


import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.tencent.rtmp.TXLiveBase;
import com.tencent.rtmp.TXLiveConstants;
import com.tencent.rtmp.ui.TXCloudVideoView;
import com.tencent.ugc.TXRecordCommon;
import com.tencent.ugc.TXUGCRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * UGC小视频录制界面
 */
public class VedioRecorderActivity extends Activity implements View.OnClickListener, TXRecordCommon.ITXVideoRecordListener {

    private static final String TAG = "TCVideoRecordActivity";
    private boolean mRecording = false;
    private boolean mStartPreview = false;
    private boolean mFront = true;
    private long mRecordTimeStamp = 0;
    private TXUGCRecord mTXCameraRecord;
    private TXRecordCommon.TXRecordResult mTXRecordResult;


    private TXCloudVideoView mVideoView;
    private ProgressBar mRecordProgress;
    private TextView mProgressTime;


    private AudioManager.OnAudioFocusChangeListener mOnAudioFocusListener;
    private boolean mPause = false;

    private RelativeLayout mRecordRalativeLayout = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.activity_video_record);
        // TXLiveBase.setLibraryPath("armeabi");
        TXLiveBase.setLibraryPath("armeabi-v7a");
//        System.loadLibrary("ijkffmpeg");
//        System.loadLibrary("ijkplayer");
//        System.loadLibrary("ijksdl");
//        System.loadLibrary("liteavsdk");
//        System.loadLibrary("saturn");
//        System.loadLibrary("TXSHA1");
        LinearLayout backLL = (LinearLayout) findViewById(R.id.back_ll);
        backLL.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mRecording && mTXCameraRecord != null) {
                    mTXCameraRecord.stopRecord();
                    mTXCameraRecord.setVideoRecordListener(null);
                }
                finish();
            }
        });

        initViews();
    }

    private void startCameraPreview() {
        if (mStartPreview) return;
        mStartPreview = true;

        TXRecordCommon.TXUGCSimpleConfig param = new TXRecordCommon.TXUGCSimpleConfig();
        param.videoQuality = TXRecordCommon.VIDEO_QUALITY_MEDIUM;
        param.isFront = mFront;
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            param.mHomeOriention = TXLiveConstants.VIDEO_ANGLE_HOME_DOWN;
        } else {
            param.mHomeOriention = TXLiveConstants.VIDEO_ANGLE_HOME_RIGHT;
        }
        Log.d(TAG, "startCameraPreview");
        mTXCameraRecord = TXUGCRecord.getInstance(this.getApplicationContext());
        mTXCameraRecord.startCameraSimplePreview(param, mVideoView);
//        mTXCameraRecord.setBeautyDepth(mBeautyParams.mBeautyStyle, mBeautyParams.mBeautyLevel, mBeautyParams.mWhiteLevel, mBeautyParams.mRuddyLevel);
//        mTXCameraRecord.setFaceScaleLevel(mBeautyParams.mFaceSlimLevel);
//        mTXCameraRecord.setEyeScaleLevel(mBeautyParams.mBigEyeLevel);
//        mTXCameraRecord.setFilter(mBeautyParams.mFilterBmp);
//        mTXCameraRecord.setGreenScreenFile(mBeautyParams.mGreenFile, true);
//        mTXCameraRecord.setMotionTmpl(mBeautyParams.mMotionTmplPath);
//        mTXCameraRecord.setFaceShortLevel(mBeautyParams.mFaceShortLevel);
//        mTXCameraRecord.setFaceVLevel(mBeautyParams.mFaceVLevel);
//        mTXCameraRecord.setChinLevel(mBeautyParams.mChinSlimLevel);
//        mTXCameraRecord.setNoseSlimLevel(mBeautyParams.mNoseScaleLevel);
    }

    private void initViews() {
//        mBeautyPannelView = (BeautySettingPannel) findViewById(R.id.beauty_pannel);
//        mBeautyPannelView.setBeautyParamsChangeListener(this);
//        mBeautyPannelView.disableExposure();
////        mBeautyPannelView.setViewVisibility(R.id.exposure_ll, View.GONE);
//
//        mAudioCtrl = (TCAudioControl) findViewById(R.id.layoutAudioControl);
        Log.d(TAG, "initViews Start");
        mVideoView = (TXCloudVideoView) findViewById(R.id.video_view);
        mVideoView.enableHardwareDecode(true);

        mProgressTime = (TextView) findViewById(R.id.progress_time);
        mRecordRalativeLayout = (RelativeLayout)findViewById(R.id.record_layout);
        Log.d(TAG, "initViews End");
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (hasPermission()) {
            startCameraPreview();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (mTXCameraRecord != null) {
            mTXCameraRecord.stopCameraPreview();
            mStartPreview = false;
        }
        stopRecord(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mTXCameraRecord != null) {
            mTXCameraRecord.stopBGM();
            mTXCameraRecord.stopCameraPreview();
            mTXCameraRecord.setVideoRecordListener(null);
            mTXCameraRecord = null;
            mStartPreview = false;
        }
        abandonAudioFocus();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        mTXCameraRecord.stopCameraPreview();
        if (mRecording) {
            stopRecord(false);
        }
        mStartPreview = false;
        startCameraPreview();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
//            case R.id.btn_beauty:
////                mBeautyPannelView.setVisibility(mBeautyPannelView.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
////                mRecordRalativeLayout.setVisibility(mBeautyPannelView.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
////                mProgressTime.setVisibility(mBeautyPannelView.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
//                break;
            case R.id.btn_switch_camera:

                mFront = !mFront;
                if (mTXCameraRecord != null) {
                    Log.d(TAG, "btn_switch_camera");
                    mTXCameraRecord.switchCamera(mFront);
                }
                break;
            case R.id.record:
                switchRecord();
                break;
//            case R.id.btn_music_pannel:
//                //  mAudioCtrl.setVisibility(View.VISIBLE);
//                break;
            case R.id.btn_confirm:
                stopRecord(true);
                break;
            default:
//                if (mBeautyPannelView.isShown()) {
//                    mBeautyPannelView.setVisibility(View.GONE);
//                    mRecordRalativeLayout.setVisibility(View.VISIBLE);
//                    mProgressTime.setVisibility(View.VISIBLE);
//                }
//                if (mAudioCtrl.isShown()) {
//                    mAudioCtrl.setVisibility(View.GONE);
//                }
                break;
        }
    }


    public int[] getSceenWH() {
        int[] screen = new int[2];
        Display display = getWindowManager().getDefaultDisplay();
        screen[0] = display.getWidth();
        screen[1] = display.getHeight();
        return screen;
    }

    private void switchRecord() {
        if (mRecording) {
            if (mPause) {
                resumeRecord();
            } else {
                pauseRecord();
            }
        } else {
            startRecord();
        }
    }

    private void resumeRecord() {
        ImageView liveRecord = (ImageView) findViewById(R.id.record);
        if (liveRecord != null) liveRecord.setBackgroundResource(R.drawable.video_stop);
        mPause = false;
        if (mTXCameraRecord != null) {
            mTXCameraRecord.resumeRecord();
        }
        requestAudioFocus();
    }

    private void pauseRecord() {
        ImageView liveRecord = (ImageView) findViewById(R.id.record);
        if (liveRecord != null) liveRecord.setBackgroundResource(R.drawable.start_record);
        mPause = true;
        if (mTXCameraRecord != null) {
            mTXCameraRecord.pauseRecord();
        }
        abandonAudioFocus();
    }

    // 录制时间要大于5s
    private void stopRecord(boolean showToast) {
        abandonAudioFocus();
        if (mRecordTimeStamp < 5 * 1000) {
            if (showToast) {
                showTooShortToast();
                return;
            } else {
                if (mTXCameraRecord != null) {
                    mTXCameraRecord.setVideoRecordListener(null);
                }
            }
        }
        if (mTXCameraRecord != null) {
            mTXCameraRecord.stopBGM();
            mTXCameraRecord.stopRecord();
        }
        ImageView liveRecord = (ImageView) findViewById(R.id.record);
        if (liveRecord != null) liveRecord.setBackgroundResource(R.drawable.start_record);
        mRecording = false;

        if (mRecordProgress != null) {
            mRecordProgress.setProgress(0);
        }
        if (mProgressTime != null) {
            mProgressTime.setText(String.format(Locale.CHINA, "%s", "00:00"));
        }
    }

    private void showTooShortToast() {
        if (mRecordProgress != null) {
            int statusBarHeight = 0;
            int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (resourceId > 0) {
                statusBarHeight = getResources().getDimensionPixelSize(resourceId);
            }

            int[] position = new int[2];
            mRecordProgress.getLocationOnScreen(position);
            Toast toast = Toast.makeText(this, "至少录到这里", Toast.LENGTH_SHORT);
            toast.setGravity(Gravity.TOP | Gravity.LEFT, position[0], position[1] - statusBarHeight - 110);
            toast.show();
        }
    }

    private void startRecord() {
        if (mTXCameraRecord == null) {
            mTXCameraRecord = TXUGCRecord.getInstance(this.getApplicationContext());
        }
        mRecordProgress = (ProgressBar) findViewById(R.id.record_progress);
        mTXCameraRecord.setVideoRecordListener(this);
        int result = mTXCameraRecord.startRecord();
        if (result != 0) {
            Toast.makeText(VedioRecorderActivity.this.getApplicationContext(), "录制失败，错误码：" + result, Toast.LENGTH_SHORT).show();
            mTXCameraRecord.setVideoRecordListener(null);
            mTXCameraRecord.stopRecord();
            return;
        }

        //   mAudioCtrl.setPusher(mTXCameraRecord);
        mRecording = true;
        mPause = false;
        ImageView liveRecord = (ImageView) findViewById(R.id.record);
        if (liveRecord != null) liveRecord.setBackgroundResource(R.drawable.video_stop);
        mRecordTimeStamp = 0;
        requestAudioFocus();
    }

    private void startPreview() {
        if (mTXRecordResult != null && mTXRecordResult.retCode == TXRecordCommon.RECORD_RESULT_OK) {
//            Intent intent = new Intent(getApplicationContext(), TCVideoPreviewActivity.class);
//            intent.putExtra(TCConstants.VIDEO_RECORD_TYPE, TCConstants.VIDEO_RECORD_TYPE_PUBLISH);
//            intent.putExtra(TCConstants.VIDEO_RECORD_RESULT, mTXRecordResult.retCode);
//            intent.putExtra(TCConstants.VIDEO_RECORD_DESCMSG, mTXRecordResult.descMsg);
//            intent.putExtra(TCConstants.VIDEO_RECORD_VIDEPATH, mTXRecordResult.videoPath);
//            intent.putExtra(TCConstants.VIDEO_RECORD_COVERPATH, mTXRecordResult.coverPath);
//            startActivity(intent);
//            finish();
            Log.d("MediaPath",String.format("resCode :{}",mTXRecordResult.retCode));
            Log.d("MediaPath",String.format("resCode :{}",mTXRecordResult.descMsg));
            Log.d("MediaPath",String.format("resCode :{}",mTXRecordResult.videoPath));
            Log.d("MediaPath",String.format("resCode :{}",mTXRecordResult.coverPath));

            //finish();
        }
    }

    @Override
    public void onRecordEvent(int event, Bundle param) {

    }

    @Override
    public void onRecordProgress(long milliSecond) {
        mRecordTimeStamp = milliSecond;
        if (mRecordProgress != null) {
            float progress = milliSecond / 10000.0f;
            mRecordProgress.setProgress((int) (progress * 100));
            mProgressTime.setText(String.format(Locale.CHINA, "00:%02d", milliSecond / 1000));
            if (milliSecond >= 60000.0f) {
                stopRecord(true);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        /** attention to this below ,must add this**/
//        UMShareAPI.get(this).onActivityResult(requestCode, resultCode, data);
//        if (resultCode == RESULT_OK) {//是否选择，没选择就不会继续
//            if (requestCode == mAudioCtrl.REQUESTCODE) {
//                if (data == null) {
//                    Log.e(TAG, "null data");
//                } else {
//                    Uri uri = data.getData();//得到uri，后面就是将uri转化成file的过程。
//                    if (mAudioCtrl != null) {
//                        mAudioCtrl.processActivityResult(uri);
//                    } else {
//                        Log.e(TAG, "NULL Pointer! Get Music Failed");
//                    }
//                }
//            }
//        }
    }

    @Override
    public void onRecordComplete(TXRecordCommon.TXRecordResult result) {
        mTXRecordResult = result;
        if (mTXRecordResult.retCode != TXRecordCommon.RECORD_RESULT_OK) {
            ImageView liveRecord = (ImageView) findViewById(R.id.record);
            if (liveRecord != null) liveRecord.setBackgroundResource(R.drawable.start_record);
            mRecording = false;

            if (mRecordProgress != null) {
                mRecordProgress.setProgress(0);
            }
            if (mProgressTime != null) {
                mProgressTime.setText(String.format(Locale.CHINA, "%s", "00:00"));
            }
            Toast.makeText(VedioRecorderActivity.this.getApplicationContext(), "录制失败，原因：" + mTXRecordResult.descMsg, Toast.LENGTH_SHORT).show();
        } else {

            if (mRecordProgress != null) {
                mRecordProgress.setProgress(0);
            }
            mProgressTime.setText(String.format(Locale.CHINA, "%s", "00:00"));
            startPreview();
        }
    }

    private void requestAudioFocus() {
        AudioManager audioManager = (AudioManager) this.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);

        if (null == mOnAudioFocusListener) {
            mOnAudioFocusListener = new AudioManager.OnAudioFocusChangeListener() {

                @Override
                public void onAudioFocusChange(int focusChange) {
                    try {
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                            mTXCameraRecord.setVideoRecordListener(null);
                            stopRecord(false);
                        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                            mTXCameraRecord.setVideoRecordListener(null);
                            stopRecord(false);
                        } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {

                        } else {
                            mTXCameraRecord.setVideoRecordListener(null);
                            stopRecord(false);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
            };
        }
        try {
            audioManager.requestAudioFocus(mOnAudioFocusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void abandonAudioFocus() {
        AudioManager audioManager = (AudioManager) this.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        try {
            audioManager.abandonAudioFocus(mOnAudioFocusListener);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

//    @Override
//    public void onBeautyParamsChange(BeautySettingPannel.BeautyParams params, int key) {
//        switch (key) {
//            case BeautySettingPannel.BEAUTYPARAM_BEAUTY:
//                mBeautyParams.mBeautyLevel = params.mBeautyLevel;
//                if (mTXCameraRecord != null) {
//                    mTXCameraRecord.setBeautyDepth(mBeautyParams.mBeautyStyle, mBeautyParams.mBeautyLevel, mBeautyParams.mWhiteLevel, mBeautyParams.mRuddyLevel);
//                }
//                break;
//            case BeautySettingPannel.BEAUTYPARAM_WHITE:
//                mBeautyParams.mWhiteLevel = params.mWhiteLevel;
//                if (mTXCameraRecord != null) {
//                    mTXCameraRecord.setBeautyDepth(mBeautyParams.mBeautyStyle, mBeautyParams.mBeautyLevel, mBeautyParams.mWhiteLevel, mBeautyParams.mRuddyLevel);
//                }
//                break;
//            case BeautySettingPannel.BEAUTYPARAM_FACE_LIFT:
//                mBeautyParams.mFaceSlimLevel = params.mFaceSlimLevel;
//                if (mTXCameraRecord != null) {
//                    mTXCameraRecord.setFaceScaleLevel(params.mFaceSlimLevel);
//                }
//                break;
//            case BeautySettingPannel.BEAUTYPARAM_BIG_EYE:
//                mBeautyParams.mBigEyeLevel = params.mBigEyeLevel;
//                if (mTXCameraRecord != null) {
//                    mTXCameraRecord.setEyeScaleLevel(params.mBigEyeLevel);
//                }
//                break;
//            case BeautySettingPannel.BEAUTYPARAM_FILTER:
//                mBeautyParams.mFilterBmp = params.mFilterBmp;
//                if (mTXCameraRecord != null) {
//                    mTXCameraRecord.setFilter(params.mFilterBmp);
//                }
//                break;
//            case BeautySettingPannel.BEAUTYPARAM_MOTION_TMPL:
//                mBeautyParams.mMotionTmplPath = params.mMotionTmplPath;
//                if (mTXCameraRecord != null) {
//                    mTXCameraRecord.setMotionTmpl(params.mMotionTmplPath);
//                }
//                break;
//            case BeautySettingPannel.BEAUTYPARAM_GREEN:
//                mBeautyParams.mGreenFile = params.mGreenFile;
//                if (mTXCameraRecord != null) {
//                    mTXCameraRecord.setGreenScreenFile(params.mGreenFile, true);
//                }
//                break;
//            case BeautySettingPannel.BEAUTYPARAM_RUDDY:
//                mBeautyParams.mRuddyLevel = params.mRuddyLevel;
//                if (mTXCameraRecord != null) {
//                    mTXCameraRecord.setBeautyDepth(mBeautyParams.mBeautyStyle, mBeautyParams.mBeautyLevel, mBeautyParams.mWhiteLevel, mBeautyParams.mRuddyLevel);
//                }
//                break;
//            case BeautySettingPannel.BEAUTYPARAM_BEAUTY_STYLE:
//                if (mTXCameraRecord != null) {
//                    mTXCameraRecord.setBeautyStyle(params.mBeautyStyle);
//                }
//                break;
//            case BeautySettingPannel.BEAUTYPARAM_FACEV:
//                if (mTXCameraRecord != null) {
//                    mTXCameraRecord.setFaceVLevel(params.mFaceVLevel);
//                }
//                break;
//            case BeautySettingPannel.BEAUTYPARAM_FACESHORT:
//                if (mTXCameraRecord != null) {
//                    mTXCameraRecord.setFaceShortLevel(params.mFaceShortLevel);
//                }
//                break;
//            case BeautySettingPannel.BEAUTYPARAM_CHINSLIME:
//                if (mTXCameraRecord != null) {
//                    mTXCameraRecord.setChinLevel(params.mChinSlimLevel);
//                }
//                break;
//            case BeautySettingPannel.BEAUTYPARAM_NOSESCALE:
//                if (mTXCameraRecord != null) {
//                    mTXCameraRecord.setNoseSlimLevel(params.mNoseScaleLevel);
//                }
//                break;
//            case BeautySettingPannel.BEAUTYPARAM_FILTER_MIX_LEVEL:
//                if (mTXCameraRecord != null) {
//                    mTXCameraRecord.setSpecialRatio(params.mFilterMixLevel/10.f);
//                }
//                break;
////            case BeautySettingPannel.BEAUTYPARAM_SHARPEN:
////                if (mTXCameraRecord != null) {
////                    mTXCameraRecord.setSharpenLevel(params.mSharpenLevel);
////                }
////                break;
//            default:
//                break;
//        }
//    }

    @Override
    public void onRequestPermissionsResult(int requestCode,String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 100:
                for (int ret : grantResults) {
                    if (ret != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                }
                startCameraPreview();
                break;
            default:
                break;
        }
    }

    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            List<String> permissions = new ArrayList<String>();

            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)) {
                permissions.add(Manifest.permission.CAMERA);
            }
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)) {
                permissions.add(Manifest.permission.RECORD_AUDIO);
            }
            if (permissions.size() != 0) {
                ActivityCompat.requestPermissions(this,
                        permissions.toArray(new String[0]),
                        100);
                return false;
            }
        }

        return true;
    }
}

