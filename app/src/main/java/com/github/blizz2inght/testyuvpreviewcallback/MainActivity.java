package com.github.blizz2inght.testyuvpreviewcallback;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import static com.github.blizz2inght.testyuvpreviewcallback.Utils.getStringFromFileInAssets;
import static com.github.blizz2inght.testyuvpreviewcallback.Utils.loadProgram;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    public static final String FRAG_SHADER_NAME = "frag_shader.glsl";
    public static final String VERTEX_SHADER_NAME = "vertex_shader.glsl";

    private String[] mPerms;

    private GLSurfaceView mPreview;
    private SurfaceTexture mSurfaceTexture;
    private CameraController mCameraController;

    private Surface mSurface;
    private int mPreviewTex;
    private int mMaxTextureSize;
//    private float[] mMVPMatrix = new float[16];
    private DisplayMetrics mDisplayMetrics = new DisplayMetrics();
    //每一行分别是顶点和纹理坐标x, y, s, t
    private float[] mVertexArr = new float[]{
            -1.f,  1.f, 0.f, 1.f,
            -1.f, -1.f, 0.f, 0.f,
             1.f, -1.f, 1.f, 0.f,
             1.f,  1.f, 1.f, 1.f
    };

    private int mPreviewProgram;
    private int a_Position;
    private int a_TexCoord;
    private int u_TextureUnit;
    private int u_TextureMatrix;
//    private int u_MVPMatrix;
    private float[] mTextureMatrix = new float[16];
    private FloatBuffer mVertexBuffer;

    private int mSW;
    private int mSH;
    private Size mPreviewSize;
    private int mVbo;
    private String mVertexShaderStr;
    private String mFragShaderStr;
    private int mYuvTex;
    private SurfaceTexture mYuvTexture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setFullScreen();
        mPerms = new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        checkPermission();
        init();
    }


    @RequiresApi(api = Build.VERSION_CODES.M)
    private void checkPermission() {
        for (String perm : mPerms) {
            final int ret = checkSelfPermission(perm);
            if (ret != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(mPerms, 201);
                break;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 201) {
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "onRequestPermissionsResult: not granted" + permissions[i]);
                }
            }
        }
    }

    private void init() {
        setContentView(R.layout.activity_main);
        mVertexShaderStr = getStringFromFileInAssets(getApplicationContext(), VERTEX_SHADER_NAME);
        mFragShaderStr = getStringFromFileInAssets(getApplicationContext(), FRAG_SHADER_NAME);
        mCameraController = new CameraController(getApplicationContext());
        mVertexBuffer = ByteBuffer.allocateDirect(mVertexArr.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mVertexBuffer.put(mVertexArr);
        mVertexBuffer.position(0);
        mPreview = findViewById(R.id.preview_view);
        mPreview.setEGLContextClientVersion(2);
        mPreview.setRenderer(new MyRender());
        mPreview.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        getWindowManager().getDefaultDisplay().getRealMetrics(mDisplayMetrics);
        mSW = mDisplayMetrics.widthPixels;
        mSH = mDisplayMetrics.heightPixels;
        mPreviewSize = mCameraController.filterPreviewSize(mSW,mSH);
    }



    private void setFullScreen() {
        Window window = getWindow();
        int viewFlags = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        int winFlags = WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS | WindowManager.LayoutParams.FLAG_FULLSCREEN;
        WindowManager.LayoutParams lp =getWindow().getAttributes();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(lp);
        }
        window.getDecorView().setSystemUiVisibility(viewFlags);
        window.addFlags(winFlags);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mPreview.onResume();

        Log.i(TAG, "onResume: " + mDisplayMetrics);
    }

    class MyRender implements GLSurfaceView.Renderer {

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {

            IntBuffer intBuffer = IntBuffer.allocate(2);

            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, intBuffer);
            mMaxTextureSize = intBuffer.get();
            Log.i(TAG, "onSurfaceCreated: max texture size="+ mMaxTextureSize);
            intBuffer.clear();

            GLES20.glGenBuffers(1, intBuffer);
            mVbo = intBuffer.get();
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbo);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mVertexBuffer.capacity() * 4, mVertexBuffer, GLES20.GL_STATIC_DRAW);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
            intBuffer.clear();

            int[] textures = new int[2];
            GLES20.glGenTextures(textures.length, textures, 0);
            //外部纹理mPreviewTex用于接收预览
            mPreviewTex = textures[0];
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mPreviewTex);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE );
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE );
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);

            mYuvTex = textures[1];
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mYuvTex);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE );
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE );
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);

            mPreviewProgram = loadProgram(mVertexShaderStr, mFragShaderStr);

            a_Position = GLES20.glGetAttribLocation(mPreviewProgram, "a_Position");
            a_TexCoord = GLES20.glGetAttribLocation(mPreviewProgram, "a_TexCoord");
            u_TextureMatrix = GLES20.glGetUniformLocation(mPreviewProgram, "u_TextureMatrix");
            u_TextureUnit = GLES20.glGetUniformLocation(mPreviewProgram, "u_TextureUnit");

            mSurfaceTexture = new SurfaceTexture(mPreviewTex);
            mSurfaceTexture.setDefaultBufferSize(mPreview.getHeight(), mPreview.getWidth());
            mSurfaceTexture.setOnFrameAvailableListener(mPreviewDataCallback, null);
            Surface surface = new Surface(mSurfaceTexture);

            mYuvTexture = new SurfaceTexture(mYuvTex);
            mSurfaceTexture.setDefaultBufferSize(mPreview.getHeight(), mPreview.getWidth());
            mSurfaceTexture.setOnFrameAvailableListener(mYuvPreviewDataCallback, null);
            mCameraController.open(surface,mYuvTexture);
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            Log.i(TAG, "onSurfaceChanged: wxh=" + width + "x" + height);

            final int x = (width - mPreviewSize.getHeight()) / 2;
            final int y = (height - mPreviewSize.getWidth()) / 2;
            GLES20.glViewport(x, y, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            GLES20.glClearColor(0, 0, 0, 1);
            mSurfaceTexture.updateTexImage();
            Matrix.setIdentityM(mTextureMatrix, /* smOffset= */ 0);
            mSurfaceTexture.getTransformMatrix(mTextureMatrix);
            //绘预览->filterTex(FBO)
            GLES20.glUseProgram(mPreviewProgram);

            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbo);
            GLES20.glVertexAttribPointer(a_Position, 2, GLES20.GL_FLOAT, false, 4 * 4, 0);
            GLES20.glEnableVertexAttribArray(a_Position);

            GLES20.glVertexAttribPointer(a_TexCoord, 2, GLES20.GL_FLOAT, false, 4 * 4, 4 * 2);
            GLES20.glEnableVertexAttribArray(a_TexCoord);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

            GLES20.glUniformMatrix4fv(
                    u_TextureMatrix, /* count= */ 1, /* transpose= */ false, mTextureMatrix, /* offset= */ 0);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mPreviewTex);
            GLES20.glUniform1i(u_TextureUnit, /* x= */ 0);

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, /* first= */ 0, /* offset= */ 4);
            GLES20.glDisableVertexAttribArray(a_Position);
            GLES20.glDisableVertexAttribArray(a_TexCoord);
        }
    }

    private SurfaceTexture.OnFrameAvailableListener mPreviewDataCallback = new SurfaceTexture.OnFrameAvailableListener() {
        @Override
        public void onFrameAvailable(SurfaceTexture surfaceTexture) {
            mPreview.requestRender();
        }
    };

    @Override
    protected void onPause() {
        super.onPause();
        mPreview.onPause();
        mCameraController.close();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCameraController.release();
    }

    private SurfaceTexture.OnFrameAvailableListener mYuvPreviewDataCallback = new SurfaceTexture.OnFrameAvailableListener() {
        @Override
        public void onFrameAvailable(SurfaceTexture surfaceTexture) {
            Log.i(TAG, "onFrameAvailable: ");
        }
    };
}
