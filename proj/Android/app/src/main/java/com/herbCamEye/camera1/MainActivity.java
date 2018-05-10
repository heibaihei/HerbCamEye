package com.herbCamEye.camera1;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.os.Process;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener, View.OnClickListener, AdapterView.OnItemClickListener{
    private String TAG = "MainActivity";
    public static final String STORAGE_PATH = Environment.getExternalStorageDirectory().toString();


    private Camera mCamera = null;
    private int mCameraId = 0; /** 0默认为前置摄像头，1为后置摄像头 */
    private int mCameraNum = 0;
    private Camera.Parameters mCameraParameters = null;
    private List<Camera.Size> mSupportedPreSizeList = null;
    private List<Camera.Size> mSupportedPicSizeList = null;
    private List<String> mPreviewSizeList = null;
    private List<String> mPictureSizeList = null;


    private Button mCaptureButton;
    private Button mSwitchCamButton;
    private Button mPictureSizeButton;
    private PopupWindow mPreviewPopupWindow;
    private PopupWindow mPicturePopupWindow;

    private ListView mPictureListView;
    private MyAdapter mPictureAdapter;
    private TextureView mTextureView;
    private int mCaptureWidth;
    private int mCaptureHeight;
    /** 存放相机的纹理ID */
    private int mOESTextureId = -1;
    private SurfaceTexture mOESSurfaceTexture = null;
    private CameraGLRenderer mImageRenderer = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTextureView = (TextureView) findViewById(R.id.camera_preview);
        mCaptureButton = (Button) findViewById(R.id.btn_capture);
        mSwitchCamButton = (Button) findViewById(R.id.btn_switchCam);
        mPictureSizeButton = (Button) findViewById(R.id.btn_pictureSize);
        /** 设置控件监听对象 */
        mCaptureButton.setOnClickListener(this);
        mSwitchCamButton.setOnClickListener(this);
        mPictureSizeButton.setOnClickListener(this);


        mImageRenderer = new CameraGLRenderer();
        if(checkCameraHardware(this)) {
            mTextureView.setSurfaceTextureListener(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        CameraInitial(mCameraId);
        CommonInitial();
    }

    /** Check whether the device has a camera */
    private boolean checkCameraHardware(Context context){
        if(context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            mCameraNum = Camera.getNumberOfCameras();
            return true;
        }
        else {
            Log.i(TAG,  "There's not camera exist !");
            return false;
        }
    }

    private void setPreviewTexture(SurfaceTexture surfaceTexture) {
        if (mCamera != null) {
            try {
                mCamera.setPreviewTexture(surfaceTexture);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //setSurfaceTextureListener
    //当SurfaceTexture可用时回调此方法，此时暂时用不到回调生成的surface，mOESSurfaceTexture内部处理surface
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {

        /** 获取外部纹理ID */
        mOESTextureId = Utils.createOESTextureObject();
        mImageRenderer.initialRenderContext(mTextureView, mOESTextureId, MainActivity.this);
        mOESSurfaceTexture = mImageRenderer.OESTextureInitial();

        mCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
        setPreviewTexture(mOESSurfaceTexture);
        setDisplayOrientation(90);
        startPreview();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {

        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        Log.i(TAG, "onSurfaceTextureUpdated");
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.btn_capture:
                takePicture();
                break;
            case R.id.btn_switchCam:
                switchCamera();
                break;
            case R.id.btn_pictureSize:
                showPopupWindow(mPicturePopupWindow, mPictureSizeButton);
                break;
            default:
                break;
        }
    }

    public void takePicture(){
        if (mCamera != null){
            mCamera.takePicture(null, null, mPictureCallback);
        }
    }

    public void switchCamera(){
        if (mCameraNum > 1){
            mCameraId = mCameraId == Camera.CameraInfo.CAMERA_FACING_BACK ?
                    Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK;
            stopPreview();
            closeCamera();

            CameraInitial(mCameraId);
            for (int i = 0; i < mPreviewSizeList.size(); i++) {
                Log.i(TAG, "preview size " + i + " :" + mPreviewSizeList.get(i));
            }

            for (int j = 0; j < mPictureSizeList.size(); j++) {
                Log.i(TAG, "picture size " + j + " :" + mPictureSizeList.get(j));
            }

            mPictureAdapter.notifyDataSetChanged();
            setDisplayOrientation(90);
            setPreviewTexture(mOESSurfaceTexture);
            startPreview();
            Log.i(TAG, "Camera has switched！");
        }else {
            Log.i(TAG, "This device does not support switch camera");
        }
    }

    private Camera.ErrorCallback mErrorCallback = new Camera.ErrorCallback() {
        @Override
        public void onError(int error, Camera camera) {
            Log.e(TAG, "onError: got camera error callback: " + error);
            if (error == Camera.CAMERA_ERROR_SERVER_DIED) {
                android.os.Process.killProcess(Process.myPid());
            }
        }
    };

    private Camera.PictureCallback mPictureCallback = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(final byte[] data, Camera camera) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    String path = STORAGE_PATH + "/DCIM" + "/CameraV1";
                    writeFile(path, data);
                }
            }, "captureThread").start();
            startPreview();
        }
    };

    public void writeFile(String path, byte[] data) {
        Bitmap bitmap = null;
        if (data != null){
            bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
        }

        if (bitmap != null){
            Matrix matrix = new Matrix();
            if (mCameraId == Camera.CameraInfo.CAMERA_FACING_BACK) {
                matrix.postRotate(90);
            }else if (mCameraId == Camera.CameraInfo.CAMERA_FACING_FRONT){
                matrix.postRotate(90);
                matrix.postScale(1, -1);
            }
            Bitmap rotateBmp = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                    bitmap.getHeight(), matrix,false);
            saveBmp2SD(path, rotateBmp);
            rotateBmp.recycle();
        }
    }

    private void saveBmp2SD(String path, Bitmap bitmap){
        File file = new File(path);
        if (!file.exists()){
            file.mkdir();
        }
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fileName = path + "/" + "IMG_" + timeStamp + ".jpg";
        try {
            FileOutputStream fos = new FileOutputStream(fileName);
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos);
            bos.flush();
            bos.close();
            Log.i(TAG, "Take picture success!");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Log.e(TAG, "The save file for take picture is not exists!");
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Take picture fail!");
        }
    }

    private void setDisplayOrientation(int degree){
        if (mCamera != null) {
            mCamera.setDisplayOrientation(degree);
            Log.i(TAG, "Set display orientation : " + degree);
        }
    }

    private void CameraInitial(int targetCameraID) {
        if (openCamera(targetCameraID) != true) {
            Log.e(TAG, "Camera open failed !");
        }
        else {
            if (mCameraParameters == null)
                mCameraParameters = getParameters();

            getSupportedPreviewSizes();
            getSupportedPictureSizes();

            if (mSupportedPreSizeList != null && mSupportedPicSizeList != null) {
                Camera.Size size = mSupportedPreSizeList.get((mSupportedPreSizeList.size() - 1));
                mCameraParameters.setPreviewSize(size.width, size.height);
                Log.i(TAG, "Work preview size paramter: " + size.width + "," + size.height);

                size = mSupportedPicSizeList.get(mSupportedPicSizeList.size() - 1);
                mCameraParameters.setPictureSize(size.width, size.height);
                Log.i(TAG, "Work picture size paramter: " + size.width + "," + size.height);
                setParameters();
            }
        }
    }

    private boolean openCamera(int targetCameraID){
        try {
            if (mCamera == null) {
                mCamera = Camera.open(targetCameraID);
                Log.i(TAG, "Camera open success, CameraID:" + targetCameraID);
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Camera open occur exception !");
        }
        return false;
    }

    private void startPreview(){
        if (mCamera != null) {
            mCamera.setErrorCallback(mErrorCallback);
            mCamera.setPreviewCallback(null);
            mCamera.startPreview();
            Log.i(TAG, "Camera Preview has started!");
        }
    }

    private void stopPreview() {
        if (mCamera != null){
            mCamera.stopPreview();
            Log.i(TAG, "Camera preview stopped!");
        }
    }

    private void closeCamera() {
        if (mCamera != null){
            mCamera.setErrorCallback(null);
            mCamera.setPreviewCallback(null);
            mCamera.release();
            mCamera = null;
            Log.i(TAG, "Camera closed!");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPreview();
        closeCamera();
    }

    private void CommonInitial() {

        mPictureAdapter = new MyAdapter(mPictureSizeList);
        mPictureListView = new ListView(this);
        mPictureListView.setId(R.id.pictureId);
        mPictureListView.setAdapter(mPictureAdapter);
        if (mPicturePopupWindow == null) {
            mPicturePopupWindow = new PopupWindow(mPictureListView, 320, 320,true);
        }
        mPictureListView.setOnItemClickListener(this);

    }

    private void showPopupWindow(PopupWindow popupWindow, View view) {
        if (popupWindow != null && view != null && !popupWindow.isShowing()) {
            int[] location = new int[2];
            view.getLocationOnScreen(location);
            popupWindow.showAtLocation(view, Gravity.NO_GRAVITY, location[0], location[1] - popupWindow.getHeight());
        } else {
            if (popupWindow != null) {
                popupWindow.dismiss();
            }
        }
    }

    private Camera.Parameters getParameters() {
        if (mCamera != null)
            return mCamera.getParameters();
        return null;
    }

    private void setParameters() {
        if(mCamera != null && mCameraParameters != null) {
            mCamera.setParameters(mCameraParameters);
        }
    }

    private List<Camera.Size> getSupportedPreviewSizes() {
        Camera.Parameters parameters = getParameters();
        if (parameters == null) {
            return null;
        }
        if (mPreviewSizeList == null) {
            mPreviewSizeList = new ArrayList<>();
        } else {
            mPreviewSizeList.clear();
        }
        mSupportedPreSizeList = new ArrayList<>();
        for (Camera.Size size : parameters.getSupportedPreviewSizes()) {
            if (equalsRate(size, 1.777f)) {
                mSupportedPreSizeList.add(size);
                Log.i(TAG,  "Support [16:9] Preview size: " + size.width + " × " + size.height);
            }
        }
        for (Camera.Size size : mSupportedPreSizeList) {
            mPreviewSizeList.add(size.width + "×" + size.height);
        }
        return mSupportedPreSizeList;
    }


    private List<Camera.Size> getSupportedPictureSizes() {
        Camera.Parameters parameters = getParameters();
        if (parameters == null) {
            return null;
        }
        if (mPictureSizeList == null) {
            mPictureSizeList = new ArrayList<>();
        } else {
            mPictureSizeList.clear();
        }
        mSupportedPicSizeList = new ArrayList<>();
        for (Camera.Size size : parameters.getSupportedPictureSizes()) {
            if(equalsRate(size, 1.777f)) {
                mSupportedPicSizeList.add(size);
                Log.i(TAG,  "Support [16:9] Picture size: " + size.width + " × " + size.height);
            }
        }
        for (Camera.Size size : mSupportedPicSizeList) {
            mPictureSizeList.add(size.width + "×" + size.height);
        }
        return mSupportedPicSizeList;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        switch (parent.getId()) {
            case R.id.pictureId:
                Log.i(TAG, "onItemClick: PictureSize: " + mPictureSizeList.get(position));
                setCaptureSize(mSupportedPicSizeList.get(position).width,
                        mSupportedPicSizeList.get(position).height);
                mPicturePopupWindow.dismiss();
                break;
            default:
                break;
        }
    }

    private void setPreviewSize(float rate) {
        if (!equalsRate(mCameraParameters.getPreviewSize(), rate)) {
            mCameraParameters.setPreviewSize(mSupportedPreSizeList.get(mSupportedPreSizeList.size() - 1).width,
                    mSupportedPreSizeList.get(mSupportedPreSizeList.size() - 1).height);
            Log.i(TAG, "setPreviewSize: changed: " + mSupportedPreSizeList.get(mSupportedPreSizeList.size() - 1).width +
                    "×" + mSupportedPreSizeList.get(mSupportedPreSizeList.size() - 1).height);
            setParameters();
            stopPreview();
            startPreview();
        } else {
            return;
        }
    }

    private void setCaptureSize(int captureWidth, int captureHeight) {
        this.mCaptureWidth = captureWidth;
        this.mCaptureHeight = captureHeight;
        mCameraParameters.setPictureSize(mCaptureWidth, mCaptureHeight);
        if (mCamera != null) {
            setParameters();
        }
        setPreviewSize((float) captureWidth / (float) captureHeight);
    }

    private boolean equalsRate(Camera.Size size, float rate){
        float f = (float)size.width / (float) size.height;
        if (Math.abs(f - rate) <= 0.1f) {
            return true;
        } else {
            return false;
        }
    }

    /** 子 View 控件 */
    class MyAdapter extends BaseAdapter {
        List<String> sizeList = new ArrayList<>();

        public MyAdapter(List<String> list) { this.sizeList = list;}

        @Override
        public int getCount() {
            return sizeList.size();
        }

        @Override
        public Object getItem(int position) {
            return sizeList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView textView = new TextView(MainActivity.this);
            textView.setTextSize(18);
            textView.setTextColor(Color.rgb(255, 255, 0));
            textView.setText(sizeList.get(position));
            return textView;
        }
    }
}
