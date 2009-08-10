LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := ocr
LOCAL_SRC_FILES := net_bitquill_ocr_Decoder.cpp

include $(BUILD_SHARED_LIBRARY)

