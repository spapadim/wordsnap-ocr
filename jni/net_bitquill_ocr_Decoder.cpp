#include <jni.h>
#include "net_bitquill_ocr_Decoder.h"

static void throwException (JNIEnv *env, const char* ex, const char* msg)
{
    if (jclass cls = env->FindClass(ex)) {
        env->ThrowNew(cls, msg);
        env->DeleteLocalRef(cls);
    }
}

inline static unsigned char getPixel (const unsigned char data[], int width, int i, int j)
{
    return data[i*width + j];
}

inline static void setPixel (unsigned char data[], int width, int i, int j, unsigned char value)
{
    data[i*width + j] = value;
}

inline static int min (int a, int b)
{
    return (a < b) ? a : b;
}

inline static int max (int a, int b)
{
    return (a > b) ? a : b;
}

JNIEXPORT jint JNICALL Java_net_bitquill_ocr_Decoder_nativeNaiveMeanFilter
  (JNIEnv *env, jclass clazz, jbyteArray jin, jbyteArray jout,
          jint width, jint height, jint radius)
{
    // Check parameters
    if (env->GetArrayLength(jin) < width*height) {
        throwException(env, "java/lang/IllegalArgumentException", "Input array too short");
        return -1;
    }
    if (env->GetArrayLength(jout) < width*height) {
        throwException(env, "java/lang/IllegalArgumentException", "Output array too short");
        return -1;
    }

    unsigned char *in = (unsigned char *) env->GetByteArrayElements(jin, 0);
    unsigned char *out = (unsigned char *) env->GetByteArrayElements(jout, 0);

    jint total = 0;
    for (int i = 0;  i < height;  i++) {
        for (int j = 0;  j < width;  j++) {
            int imin = max(0, i-radius), imax = min(height-1, i+radius);
            int jmin = max(0, j-radius), jmax = min(width-1, j+radius);
            int sum = 0;
            for (int si = imin;  si <= imax;  si++) {
                for (int sj = jmin;  sj <= jmax;  sj++) {
                    sum += getPixel(in, width, si, sj);
                }
            }
            setPixel(out, width, i, j, (unsigned char)(sum/((imax-imin+1)*(jmax-jmin+1))));
            total += getPixel(in, width, i, j);
        }
    }

    env->ReleaseByteArrayElements(jin, (jbyte *)in, 0);
    env->ReleaseByteArrayElements(jout, (jbyte *)out, 0);

    return total/(width*height);
}

static int rowSumIncDec (int* sum,
        int rowInc, int rowDec,
        const unsigned char* data,
        int width, int height, int radius)
{
    int total = 0;  // Total of rowInc-th row entries
    int deltaInc = 0, deltaDec = 0;

    // Handle first element
    for (int sj = 0;  sj <= radius;  sj++) {
        if (rowInc < height) {
            int val = getPixel(data, width, rowInc, sj);
            deltaInc += val;
            total += val;  // Increment here, so we read off pixel only once
        }
        if (rowDec >= 0) {
            deltaDec += getPixel(data, width, rowDec, sj);
        }
    }
    sum[0] += deltaInc - deltaDec;

    // Incrementally deal with remaining elements
    // Broken into three for loops to avoid if statements inside loop
    for (int j = 1;  j < radius + 1;  j++) {
        // Left edge
        if (rowInc < height) {
            deltaInc += getPixel(data, width, rowInc, j+radius);
        }
        if (rowDec >= 0) {
            deltaDec += getPixel(data, width, rowDec, j+radius);
        }
        sum[j] += deltaInc - deltaDec;
    }
    for (int j = radius + 1;  j < width - radius;  j++) {
        // Internal
        if (rowInc < height) {
            deltaInc -= getPixel(data, width, rowInc, j-radius-1);
            int val = getPixel(data, width, rowInc, j+radius);
            deltaInc += val;
            total += val; // Increment here, so we read off pixel only once
        }
        if (rowDec >= 0) {
            deltaDec -= getPixel(data, width, rowDec, j-radius-1);
            deltaDec += getPixel(data, width, rowDec, j+radius);
        }
        sum[j] += deltaInc - deltaDec;
    }
    for (int j = width - radius;  j < width;  j++) {
        // Right edge
        if (rowInc < height) {
            deltaInc -= getPixel(data, width, rowInc, j-radius-1);
        }
        if (rowDec >= 0) {
            deltaDec -= getPixel(data, width, rowDec, j-radius-1);
        }
        sum[j] += deltaInc - deltaDec;
    }

    return total;
}

static void avgRow (const int* sum, int row,
        unsigned char* data, int width, int height, int radius)
{
    // Compute clipped height
    int h = min(height-1, row+radius) - max(0, row-radius) + 1;
    for (int j = 0;  j < width;  j++) {
        // Compute clipped width
        int w = min(width-1, j+radius) - max(0, j-radius) + 1;
        setPixel(data, width, row, j, (unsigned char)(sum[j]/(w*h)));
    }
}

JNIEXPORT jint JNICALL Java_net_bitquill_ocr_Decoder_nativeMeanFilter1
  (JNIEnv *env, jclass clazz, jbyteArray jin, jbyteArray jout,
          jint width, jint height, jint radius)
{
    // Check parameters
    if (env->GetArrayLength(jin) < width*height) {
        throwException(env, "java/lang/IllegalArgumentException", "Input array too short");
        return -1;
    }
    if (env->GetArrayLength(jout) < width*height) {
        throwException(env, "java/lang/IllegalArgumentException", "Output array too short");
        return -1;
    }
    int* sum = new int[width];
    if (sum == 0) {
        throwException(env, "java/lang/OutOfMemoryError", "Failed to allocate sums buffer");
        return -1;
    }

    unsigned char *in = (unsigned char *) env->GetByteArrayElements(jin, 0);
    unsigned char *out = (unsigned char *) env->GetByteArrayElements(jout, 0);

    int total = 0;

    for (int si = 0;  si <= radius;  si++) {
        total += rowSumIncDec(sum, si, -1, in, width, height, radius);
    }
    avgRow(sum, 0, out, width, height, radius);

    for (int i = 1;  i < height; i++) {
        total += rowSumIncDec(sum, i+radius, i-radius-1, in, width, height, radius);
        avgRow(sum, i, out, width, height, radius);
    }

    delete sum;
    env->ReleaseByteArrayElements(jin, (jbyte *)in, 0);
    env->ReleaseByteArrayElements(jout, (jbyte *)out, 0);

    return total/(width*height);
}
