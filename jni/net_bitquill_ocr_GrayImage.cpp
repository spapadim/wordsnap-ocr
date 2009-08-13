#include <jni.h>
#include "net_bitquill_ocr_GrayImage.h"

static void throwException (JNIEnv *env, const char* ex, const char* msg)
{
    if (jclass cls = env->FindClass(ex)) {
        env->ThrowNew(cls, msg);
        env->DeleteLocalRef(cls);
    }
}

inline static int linearIndex (int width, int i, int j) {
    return i*width + j;
}

template <class T>
inline static T getPixel (const T data[], int width, int i, int j)
{
    return data[linearIndex(width, i, j)];
}

template <class T>
inline static void setPixel (T data[], int width, int i, int j, T value)
{
    data[linearIndex(width, i, j)] = value;
}

template <class T>
inline static T min (T a, T b)
{
    return (a < b) ? a : b;
}

template <class T>
inline static T max (T a, T b)
{
    return (a > b) ? a : b;
}

static void rowSumIncDec (int* sum,
        int rowInc, int rowDec,
        const unsigned char* data,
        int width, int height, int radius)
{
    int deltaInc = 0, deltaDec = 0;

    // Handle first element
    for (int sj = 0;  sj <= radius;  sj++) {
        if (rowInc < height) {
            deltaInc += getPixel(data, width, rowInc, sj);
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
            deltaInc += getPixel(data, width, rowInc, j+radius);
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

JNIEXPORT void JNICALL Java_net_bitquill_ocr_GrayImage_nativeMeanFilter
  (JNIEnv *env, jclass cls, jbyteArray jin, jbyteArray jout,
          jint width, jint height, jint radius)
{
    // Check parameters
    if (width < 0 || height < 0) {
        throwException(env, "java/lang/IllegalArgumentException", "Width and height must be non-negative");
        return;
    }
    if (env->GetArrayLength(jin) < width*height) {
        throwException(env, "java/lang/IllegalArgumentException", "Input array too short");
        return;
    }
    if (env->GetArrayLength(jout) < width*height) {
        throwException(env, "java/lang/IllegalArgumentException", "Output array too short");
        return;
    }
    int* sum = new int[width];
    if (sum == 0) {
        throwException(env, "java/lang/OutOfMemoryError", "Failed to allocate sums buffer");
        return;
    }

    unsigned char *in = (unsigned char *) env->GetByteArrayElements(jin, 0);
    unsigned char *out = (unsigned char *) env->GetByteArrayElements(jout, 0);

    for (int si = 0;  si <= radius;  si++) {
        rowSumIncDec(sum, si, -1, in, width, height, radius);
    }
    avgRow(sum, 0, out, width, height, radius);

    for (int i = 1;  i < height; i++) {
        rowSumIncDec(sum, i+radius, i-radius-1, in, width, height, radius);
        avgRow(sum, i, out, width, height, radius);
    }

    delete sum;
    env->ReleaseByteArrayElements(jin, (jbyte *)in, 0);
    env->ReleaseByteArrayElements(jout, (jbyte *)out, 0);
}

JNIEXPORT jint JNICALL Java_net_bitquill_ocr_GrayImage_nativeMean
  (JNIEnv *env, jclass cls, jbyteArray jin, jint width, jint height)
{
    // Check parameters
    if (width < 0 || height < 0) {
        throwException(env, "java/lang/IllegalArgumentException", "Width and height must be non-negative");
        return -1;
    }
    if (env->GetArrayLength(jin) < width*height) {
        throwException(env, "java/lang/IllegalArgumentException", "Input array too short");
        return -1;
    }

    unsigned char *in = (unsigned char *) env->GetByteArrayElements(jin, 0);

    int total = 0;
    for (int i = 0;  i < height;  i++) {
        for (int j = 0;  j < width;  j++) {
            total += getPixel(in, width, i, j);
        }
    }

    env->ReleaseByteArrayElements(jin, (jbyte *)in, 0);

    return total / (width * height);
}

JNIEXPORT void JNICALL Java_net_bitquill_ocr_GrayImage_nativeGrayToARGB
  (JNIEnv *env, jclass cls,
          jbyteArray jin, jint imgWidth, jint imgHeight,
          jintArray jout, jint left, jint top, jint width, jint height)
{
    // Check parameters
    if (width < 0 || height < 0 || imgWidth < 0 || imgHeight < 0) {
        throwException(env, "java/lang/IllegalArgumentException", "Width and height must be non-negative");
        return;
    }
    if (env->GetArrayLength(jout) < width * height) {
        throwException(env, "java/lang/IllegalArgumentException", "Output array too short");
        return;
    }
    if (env->GetArrayLength(jin) < imgWidth * imgHeight) {
        throwException(env, "java/lang/IllegalArgumentException", "Input array too short");
        return;
    }
    if (left < 0 || left + width >= imgWidth || top < 0 || top + height >= imgHeight) {
        throwException(env, "java/lang/IllegalArgumentException", "ROI exceeds image");
        return;
    }

    unsigned char *in = (unsigned char *) env->GetByteArrayElements(jin, 0);
    unsigned int *out = (unsigned int *) env->GetIntArrayElements(jout, 0);

    for (int i = 0;  i < height;  i++) {
        for (int j = 0;  j < width;  j++) {
            unsigned char gray = getPixel(in, imgWidth, top + i, left + j);
            unsigned int argb = (0xFF << 24) | (gray << 16) | (gray < 8) | gray;
            setPixel(out, width, i, j, argb);
        }
    }

    env->ReleaseByteArrayElements(jin, (jbyte *)in, 0);
    env->ReleaseIntArrayElements(jout, (jint *)out, 0);
}

JNIEXPORT void JNICALL Java_net_bitquill_ocr_GrayImage_nativeAdaptiveThreshold
  (JNIEnv *env, jclass cls,
    jbyteArray jin, jbyteArray jthresh, jbyteArray jout,
    jint width, jint height, jbyte hi, jbyte lo, jint offset)
{
    // Check parameters
    if (env->GetArrayLength(jin) < width * height) {
        throwException(env, "java/lang/IllegalArgumentException", "Input array too short");
        return;
    }
    if (env->GetArrayLength(jthresh) < width * height) {
        throwException(env, "java/lang/IllegalArgumentException", "Threshold array too short");
        return;
    }
    if (env->GetArrayLength(jout) < width * height) {
        throwException(env, "java/lang/IllegalArgumentException", "Output array too short");
        return;
    }
    if (width < 0 || height < 0) {
         throwException(env, "java/lang/IllegalArgumentException", "Width and height must be non-negative");
         return;
     }

    unsigned char *in = (unsigned char *) env->GetByteArrayElements(jin, 0);
    unsigned char *thresh = (unsigned char *) env->GetByteArrayElements(jthresh, 0);
    unsigned char *out = (unsigned char *) env->GetByteArrayElements(jout, 0);

    for (int i = 0;  i < height;  i++) {
        for (int j = 0;  j < width;  j++) {
            unsigned char thr = getPixel(in, width, i, j);
            unsigned char val = getPixel(thresh, width, i, j);
            setPixel(out, width, i, j, (thr - val < offset) ? (unsigned char)hi : (unsigned char)lo);
        }
    }

    env->ReleaseByteArrayElements(jin, (jbyte *)in, 0);
    env->ReleaseByteArrayElements(jthresh, (jbyte *)thresh, 0);
    env->ReleaseByteArrayElements(jout, (jbyte *)out, 0);
}

template<class Op>
static inline unsigned char borderStructuralTransform
    (const unsigned char* in, int width, int height,
            int i0, int j0,
            int numNeighbors,
            const int *hOffsets, const int *vOffsets,
            Op op, unsigned char val0)
{
    unsigned char val = val0;
    for (int n = 0;  n < numNeighbors;  n++) {
        int i = i0 + vOffsets[n];
        int j = j0 + hOffsets[n];
        if (i >= 0 && i < height && j >= 0 && j < width) {
            val = op(val, getPixel(in, width, i, j));
        }
    }
    return val;
}

template<class Op>
static void structuralTransform
  (JNIEnv *env, jclass cls,
          jbyteArray jin, jbyteArray jout, jint width, jint height,
          jint numNeighbors,
          jintArray jhOffsets, jintArray jvOffsets,
          jintArray jlinearOffsets,
          jint minX, jint maxX, jint minY, jint maxY,
          Op op, unsigned char val0)
{
    // Check parameters
    if (env->GetArrayLength(jin) < width * height) {
        throwException(env, "java/lang/IllegalArgumentException", "Input array too short");
        return;
    }
    if (env->GetArrayLength(jout) < width * height) {
        throwException(env, "java/lang/IllegalArgumentException", "Output array too short");
        return;
    }
    if (width < 0 || height < 0) {
         throwException(env, "java/lang/IllegalArgumentException", "Width and height must be non-negative");
         return;
    }
    // TODO - more:
    // minX,minY <= 0  maxX, maxY >= 0
    // numNeighbors >= 1
    // offset array lengths == numNeighbors

    unsigned char *in = (unsigned char *) env->GetByteArrayElements(jin, 0);
    unsigned char *out = (unsigned char *) env->GetByteArrayElements(jout, 0);
    int *hOffsets = (int *) env->GetIntArrayElements(jhOffsets, 0);
    int *vOffsets = (int *) env->GetIntArrayElements(jvOffsets, 0);
    int *linearOffsets = (int *) env->GetIntArrayElements(jlinearOffsets, 0);

    // Top edge
    for (int i0 = 0;  i0 < -minY;  i0++) {
        for (int j0 = 0;  j0 < width;  j0++) {
            setPixel(out, width, i0, j0,
                    borderStructuralTransform(in, width, height,
                            i0, j0, numNeighbors, hOffsets, vOffsets, op, val0));
        }
    }
    // Bottom edge
    for (int i0 = height - maxY;  i0 < height;  i0++) {
        for (int j0 = 0;  j0 < width;  j0++) {
            setPixel(out, width, i0, j0,
                    borderStructuralTransform(in, width, height,
                            i0, j0, numNeighbors, hOffsets, vOffsets, op, val0));
        }
    }
    // Left edge
    for (int i0 = -minY;  i0 < height - maxY;  i0++) {
        for (int j0 = 0;  j0 < -minX;  j0++) {
            setPixel(out, width, i0, j0,
                    borderStructuralTransform(in, width, height,
                            i0, j0, numNeighbors, hOffsets, vOffsets, op, val0));
        }
    }
    // Right edge
    for (int i0 = -minY;  i0 < height - maxY;  i0++) {
        for (int j0 = width - maxX;  j0 < width;  j0++) {
            setPixel(out, width, i0, j0,
                    borderStructuralTransform(in, width, height,
                            i0, j0, numNeighbors, hOffsets, vOffsets, op, val0));
        }
    }

    // Interior pixels
    for (int i0 = -minY;  i0 < height - maxY;  i0++) {
        for (int j0 = -minX;  j0 < width - maxX;  j0++) {
            int lin0 = linearIndex(width, i0, j0);
            unsigned char val = in[lin0 + linearOffsets[0]];
            for (int n = 1;  n < numNeighbors;  n++) {
                val = op(val, in[lin0 + linearOffsets[n]]);
            }
            setPixel(out, width, i0, j0, val);
        }
    }

    env->ReleaseByteArrayElements(jin, (jbyte *)in, 0);
    env->ReleaseByteArrayElements(jout, (jbyte *)out, 0);
    env->ReleaseIntArrayElements (jhOffsets, (jint *)hOffsets, 0);
    env->ReleaseIntArrayElements (jvOffsets, (jint *)vOffsets, 0);
    env->ReleaseIntArrayElements (jlinearOffsets, (jint *)linearOffsets, 0);
}

static struct MinOp
{
    inline unsigned char operator () (unsigned char a, unsigned char b) {
        return (a < b) ? a : b;
    }
} minOp;

JNIEXPORT void JNICALL Java_net_bitquill_ocr_GrayImage_nativeErode
  (JNIEnv *env, jclass cls,
          jbyteArray jin, jbyteArray jout, jint width, jint height,
          jint numNeighbors,
          jintArray jhOffsets, jintArray jvOffsets,
          jintArray jlinearOffsets,
          jint minX, jint maxX, jint minY, jint maxY)
{
    structuralTransform(env, cls,
            jin, jout, width, height,
            numNeighbors, jhOffsets, jvOffsets, jlinearOffsets,
            minX, maxX, minY, maxY,
            minOp, 255);
}

static struct maxOp
{
    inline unsigned char operator () (unsigned char a, unsigned char b) {
        return (a > b) ? a : b;
    }
} maxOp;

JNIEXPORT void JNICALL Java_net_bitquill_ocr_GrayImage_nativeDilate
  (JNIEnv *env, jclass cls,
          jbyteArray jin, jbyteArray jout, jint width, jint height,
          jint numNeighbors,
          jintArray jhOffsets, jintArray jvOffsets,
          jintArray jlinearOffsets,
          jint minX, jint maxX, jint minY, jint maxY)
{
    structuralTransform(env, cls,
            jin, jout, width, height,
            numNeighbors, jhOffsets, jvOffsets, jlinearOffsets,
            minX, maxX, minY, maxY,
            maxOp, 0);
}
