#include <jni.h>
#include <string>
#include <android/log.h>
#include <opencv2/opencv.hpp>
#include <opencv2/imgproc.hpp>

#define LOG_TAG "NativeProcessor"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Processing modes
const int MODE_RAW = 0;
const int MODE_GRAYSCALE = 1;
const int MODE_CANNY = 2;

// Canny edge detection parameters
const double CANNY_THRESHOLD1 = 50.0;
const double CANNY_THRESHOLD2 = 150.0;

extern "C" {

/**
 * Test native library connection
 */
JNIEXPORT jstring JNICALL
Java_com_flam_edgeviewer_NativeProcessor_testConnection(JNIEnv *env, jobject /* this */) {
    std::string message = "OpenCV " + std::string(CV_VERSION) + " loaded successfully";
    LOGD("%s", message.c_str());
    return env->NewStringUTF(message.c_str());
}

/**
 * Convert YUV_420_888 to RGBA
 */
JNIEXPORT jobject JNICALL
Java_com_flam_edgeviewer_NativeProcessor_yuv420ToRgba(
        JNIEnv *env,
        jobject /* this */,
        jobject yuvBuffer,
        jint width,
        jint height) {

    try {
        // Get YUV data
        uint8_t *yuvData = (uint8_t *) env->GetDirectBufferAddress(yuvBuffer);
        if (yuvData == nullptr) {
            LOGE("Failed to get YUV buffer address");
            return nullptr;
        }

        // Create YUV Mat (NV21 format)
        cv::Mat yuvMat(height + height / 2, width, CV_8UC1, yuvData);
        cv::Mat rgbaMat(height, width, CV_8UC4);

        // Convert YUV to RGBA
        cv::cvtColor(yuvMat, rgbaMat, cv::COLOR_YUV2RGBA_NV21);

        // Create output buffer
        jlong bufferSize = (jlong) rgbaMat.total() * rgbaMat.elemSize();
        jobject outputBuffer = env->NewDirectByteBuffer(rgbaMat.data, bufferSize);

        return outputBuffer;

    } catch (const cv::Exception &e) {
        LOGE("OpenCV error in yuv420ToRgba: %s", e.what());
        return nullptr;
    } catch (...) {
        LOGE("Unknown error in yuv420ToRgba");
        return nullptr;
    }
}

/**
 * Process camera frame with OpenCV
 */
JNIEXPORT jobject JNICALL
Java_com_flam_edgeviewer_NativeProcessor_processFrame(
        JNIEnv *env,
        jobject /* this */,
        jobject yuvBuffer,
        jint width,
        jint height,
        jint mode) {

    try {
        // Get YUV data
        uint8_t *yuvData = (uint8_t *) env->GetDirectBufferAddress(yuvBuffer);
        if (yuvData == nullptr) {
            LOGE("Failed to get YUV buffer address");
            return nullptr;
        }

        // Create YUV Mat (NV21 format - standard Android YUV_420_888)
        cv::Mat yuvMat(height + height / 2, width, CV_8UC1, yuvData);
        cv::Mat rgbaMat(height, width, CV_8UC4);

        // Convert YUV to RGBA
        cv::cvtColor(yuvMat, rgbaMat, cv::COLOR_YUV2RGBA_NV21);

        cv::Mat processedMat;

        switch (mode) {
            case MODE_RAW:
                // Return raw RGBA
                processedMat = rgbaMat.clone();
                break;

            case MODE_GRAYSCALE: {
                // Convert to grayscale
                cv::Mat grayMat;
                cv::cvtColor(rgbaMat, grayMat, cv::COLOR_RGBA2GRAY);
                
                // Convert back to RGBA for display
                cv::cvtColor(grayMat, processedMat, cv::COLOR_GRAY2RGBA);
                break;
            }

            case MODE_CANNY: {
                // Convert to grayscale first
                cv::Mat grayMat;
                cv::cvtColor(rgbaMat, grayMat, cv::COLOR_RGBA2GRAY);

                // Apply Gaussian blur to reduce noise
                cv::Mat blurredMat;
                cv::GaussianBlur(grayMat, blurredMat, cv::Size(5, 5), 1.5);

                // Apply Canny edge detection
                cv::Mat edgesMat;
                cv::Canny(blurredMat, edgesMat, CANNY_THRESHOLD1, CANNY_THRESHOLD2);

                // Convert edges to RGBA (white edges on black background)
                cv::cvtColor(edgesMat, processedMat, cv::COLOR_GRAY2RGBA);
                break;
            }

            default:
                LOGE("Unknown processing mode: %d", mode);
                processedMat = rgbaMat.clone();
                break;
        }

        // Allocate output buffer
        jlong bufferSize = (jlong) processedMat.total() * processedMat.elemSize();
        
        // Create persistent buffer (needs to be freed by Java side or reused)
        uint8_t *outputData = new uint8_t[bufferSize];
        memcpy(outputData, processedMat.data, bufferSize);
        
        jobject outputBuffer = env->NewDirectByteBuffer(outputData, bufferSize);

        return outputBuffer;

    } catch (const cv::Exception &e) {
        LOGE("OpenCV error in processFrame: %s", e.what());
        return nullptr;
    } catch (const std::exception &e) {
        LOGE("Standard exception in processFrame: %s", e.what());
        return nullptr;
    } catch (...) {
        LOGE("Unknown error in processFrame");
        return nullptr;
    }
}

} // extern "C"
