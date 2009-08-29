LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := imageproc-adc
LOCAL_SRC_FILES := net_bitquill_adc_ocr_image_GrayImage.cpp

include $(BUILD_SHARED_LIBRARY)

