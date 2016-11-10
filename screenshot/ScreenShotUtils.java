package cn.com.jfyuan.utils.screenshot;

import java.nio.ByteBuffer;
import java.util.List;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.view.View;
import android.view.WindowManager;

import cn.com.jfyuan.utils.ScreenParams;

public class ScreenShotUtils {
	
	public final static int requestCode = 10086;
	private MediaProjection mMediaProjection;
	private VirtualDisplay mVirtualDisplay;
	private MediaProjectionManager mMediaProjectionManager;
	private ImageReader imageReader;
	private int mResultCode;
    private Intent mResultData;
    private boolean isConfirmShowing = false;
    
    /**
     * android 5.0以下 截屏
     */
	public Bitmap getScreenshotBitmap(Activity activity) {
		if (activity == null) {
			throw new IllegalArgumentException("Parameter activity cannot be null.");
		}
		final List<RootViewInfo> viewRoots = FieldHelper.getRootViews(activity);
		View main = activity.getWindow().getDecorView();
		final Bitmap bitmap;
		try {
			bitmap = Bitmap.createBitmap(main.getWidth(), main.getHeight(),Bitmap.Config.ARGB_8888);
		} catch (final IllegalArgumentException e) {
			return null;
		}
		drawRootsToBitmap(viewRoots, bitmap);
		return bitmap;
	}
	/**
     * android 5.0以上截屏
     * <p>使用过程中会以{@link Activity#startActivityForResult(Intent, int)}弹出权限确认框，
     * 需要在{@link Activity#onActivityResult(int,int,Intent)}中调用{@link #setMediaProjectionResultData(int, Intent)}才能正常截屏</p>
     * <b><font color="#fd3131">完成后记得调用{@link #tearDownMediaProjection()}停止并释放资源</font></b>
     */
	@SuppressLint("NewApi")
	public Bitmap getScreenshotBitmap5(Activity activity){
		if(null == imageReader){
			imageReader=ImageReader.newInstance(ScreenParams.getScreenWidth(activity), ScreenParams.getScreenHeight(activity), 0x1, 2);
		}
		if (mMediaProjection != null) {
            setUpVirtualDisplay(activity);
        } else if (mResultCode != 0 && mResultData != null) {
            setUpMediaProjection();
            setUpVirtualDisplay(activity);
        } else {
        	if(null == mMediaProjectionManager){
        		mMediaProjectionManager = (MediaProjectionManager)activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        	}
        	if(!isConfirmShowing){
        		activity.startActivityForResult(mMediaProjectionManager.createScreenCaptureIntent(), requestCode);
        		isConfirmShowing=true;
        	}
        }
		Image image = imageReader.acquireLatestImage();
		if (null == image)
			return null;
		int width = image.getWidth();
		int height = image.getHeight();
		final Image.Plane[] planes = image.getPlanes();
		final ByteBuffer buffer = planes[0].getBuffer();
		buffer.rewind();
		int pixelStride = planes[0].getPixelStride();
		int rowStride = planes[0].getRowStride();
		int rowPadding = rowStride - pixelStride * width;
		Bitmap bmp = Bitmap.createBitmap(width+rowPadding/pixelStride, height, Bitmap.Config.ARGB_8888);
		bmp.copyPixelsFromBuffer(buffer);
		Bitmap result = Bitmap.createBitmap(bmp, 0, 0, width, height);
		image.close();
		return result;
	}
	/**
	 * 释放截屏资源
	 */
	@SuppressLint("NewApi")
	public void tearDownMediaProjection() {
		if (mMediaProjection != null) {
			mMediaProjection.stop();
			mMediaProjection = null;
		}
		if (mVirtualDisplay != null) {
			mVirtualDisplay.release();
			mVirtualDisplay = null;
		}
	}
	/**
	 *  确认截屏权限
	 * @param resultCode
	 * @param resultData
	 */
	public void setMediaProjectionResultData(int resultCode,Intent resultData){
		if(resultCode == Activity.RESULT_OK){
			this.mResultCode=resultCode;
			this.mResultData=resultData;
		}
		isConfirmShowing = false;
	}
	
	@SuppressLint("NewApi")
	private void setUpMediaProjection() {
		if(null == mMediaProjection){
			mMediaProjection = mMediaProjectionManager.getMediaProjection(mResultCode, mResultData);
		}
    }
	
	@SuppressLint("NewApi")
	private void setUpVirtualDisplay(Activity activity) {
		if(null == mVirtualDisplay){
			mVirtualDisplay = mMediaProjection.createVirtualDisplay("ScreenCapture",
					ScreenParams.getScreenWidth(activity),
					ScreenParams.getScreenHeight(activity), 
					ScreenParams.getDimension(activity),
					DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
					imageReader.getSurface(), null, null);
		}
	}

	private void drawRootsToBitmap(List<RootViewInfo> viewRoots,Bitmap bitmap) {
		if (null != viewRoots) {
			for (RootViewInfo rootData : viewRoots) {
				drawRootToBitmap(rootData, bitmap);
			}
		}
	}

	private void drawRootToBitmap(final RootViewInfo rootViewInfo,Bitmap bitmap) {
		if ((rootViewInfo.getLayoutParams().flags & WindowManager.LayoutParams.FLAG_DIM_BEHIND) == WindowManager.LayoutParams.FLAG_DIM_BEHIND) {
			Canvas dimCanvas = new Canvas(bitmap);
			int alpha = (int) (255 * rootViewInfo.getLayoutParams().dimAmount);
			dimCanvas.drawARGB(alpha, 0, 0, 0);
		}
		final Canvas canvas = new Canvas(bitmap);
		canvas.translate(rootViewInfo.getRect().left,rootViewInfo.getRect().top);
		rootViewInfo.getView().draw(canvas);
	}
}
