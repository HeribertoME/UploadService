package com.hmelizarraraz.myapplicationservice;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v4.app.JobIntentService;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.hmelizarraraz.myapplicationservice.service.CountingRequestBody;
import com.hmelizarraraz.myapplicationservice.service.RestApiService;
import com.hmelizarraraz.myapplicationservice.service.RetrofitInstance;
import com.hmelizarraraz.myapplicationservice.utils.MIMEType;

import java.io.File;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.FlowableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

public class FileUploadService extends JobIntentService {

    private static final String TAG = "FileUploadService";
    Disposable disposable;
    /**
     * Unique job ID for this service
     */
    private static final int JOB_ID = 102;

    public static void enqueueWork(Context context, Intent intent) {
        enqueueWork(context, FileUploadService.class, JOB_ID, intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        /**
         * Download/Upload of file
         * The system or framework is already holding a wake lock for us at this point
         */

        // get file here
        final String filePath = intent.getStringExtra("mFilePath");
        if (filePath == null) {
            Log.e(TAG, "onHandleWork: Invalida file URI");
            return;
        }

        final RestApiService apiService = RetrofitInstance.getApiService();
        Flowable<Double> fileObservable = Flowable.create(new FlowableOnSubscribe<Double>() {
            @Override
            public void subscribe(FlowableEmitter<Double> emitter) throws Exception {
                apiService.onFileUpload(
                        createRequestBodyFromText("algo@mail.com"),
                        createMultipartBody(filePath, emitter)
                )
                        .blockingGet();
                emitter.onComplete();
            }
        }, BackpressureStrategy.LATEST);

        disposable = fileObservable.subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        new Consumer<Double>() {
                            @Override
                            public void accept(Double progress) throws Exception {
                                FileUploadService.this.onProgress(progress);
                            }},
                        new Consumer<Throwable>() {
                            @Override
                            public void accept(Throwable throwable) throws Exception {
                                FileUploadService.this.onErrors(throwable);
                            }},
                        new Action() {
                            @Override
                            public void run() throws Exception {
                                FileUploadService.this.onSuccess();
                            }});
    }

    private void onErrors(Throwable throwable) {
        sendBroadcastMessage("Error in file upload " + throwable.getMessage());
        Log.e(TAG, "onErrors: ", throwable);
    }

    private void onProgress(Double progress) {
        sendBroadcastMessage("Uploading in progress..." + (int) (100 * progress));
        Log.i(TAG, "onProgress: " + progress);
    }

    private void onSuccess() {
        sendBroadcastMessage("File uploading successful");
        Log.i(TAG, "onSuccess: File Uploaded");
    }

    private void sendBroadcastMessage(String message) {
        Intent localIntent = new Intent("my.own.broadcast");
        localIntent.putExtra("result", message);
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }

    private RequestBody createRequestBodyFromFile(File file, String mimeType) {
        return RequestBody.create(MediaType.parse(mimeType), file);
    }

    private RequestBody createRequestBodyFromText(String text) {
        return RequestBody.create(MediaType.parse("text/plain"), text);
    }

    /**
     * return multi part body in format of FlowableEmitter
     * @param filePath
     * @param emitter
     * @return
     */
    private MultipartBody.Part createMultipartBody(String filePath, FlowableEmitter<Double> emitter) {
        File file = new File(filePath);
        return MultipartBody.Part.createFormData("myFile", file.getName(), createCountingRequestBody(file, MIMEType.IMAGE.value, emitter));
    }

    private RequestBody createCountingRequestBody(File file, String mimeType, final FlowableEmitter<Double> emitter) {
        RequestBody requestBody = createRequestBodyFromFile(file, mimeType);
        return new CountingRequestBody(requestBody, new CountingRequestBody.Listener() {
            @Override
            public void onRequestProgress(long bytesWritten, long contentLength) {
                double progress = (1.0 * bytesWritten) / contentLength;
                emitter.onNext(progress);
            }
        });
    }


}
