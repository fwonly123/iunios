LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

#LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_SRC_FILES := $(call all-java-files-under,$(src_dirs)) \
         src/com/secure/service/IAdBlockService.aidl \
         src/com/secure/service/IAdBlockServiceCallback.aidl \
         src/com/android/internal/policy/IKeyguardExitCallback.aidl \
         src/com/android/internal/policy/IKeyguardService.aidl \
         src/com/android/internal/policy/IKeyguardShowCallback.aidl

LOCAL_PACKAGE_NAME := Aurora_Hook

include $(BUILD_PACKAGE)

include $(call all-makefiles-under,$(LOCAL_PATH))
