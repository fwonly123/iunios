#     
# Copyright (c) 2011, QUALCOMM Incorporated. 
# All Rights Reserved. 
# QUALCOMM Proprietary and Confidential.
# Developed by QRD Engineering team.
#
ifeq ("$(GN_APP_AUDIO_PROFILE_SUPPORT)","yes")

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-subdir-java-files)

LOCAL_JAVA_LIBRARIES += mediatek-framework

LOCAL_PACKAGE_NAME := Aurora_AccountSetting

LOCAL_CERTIFICATE := platform

include $(BUILD_PACKAGE)

endif