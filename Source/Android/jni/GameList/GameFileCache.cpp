// Copyright 2018 Dolphin Emulator Project
// SPDX-License-Identifier: GPL-2.0-or-later

#include <memory>
#include <vector>

#include <jni.h>

#include "UICommon/GameFileCache.h"
#include "jni/AndroidCommon/AndroidCommon.h"
#include "jni/AndroidCommon/IDCache.h"
#include "jni/GameList/GameFile.h"

namespace UICommon
{
class GameFile;
}

static UICommon::GameFileCache* GetPointer(JNIEnv* env, jobject obj)
{
  return reinterpret_cast<UICommon::GameFileCache*>(
      env->GetLongField(obj, IDCache::GetGameFileCachePointer()));
}

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jlong JNICALL
Java_org_dolphinemu_dolphinemu_model_GameFileCache_newGameFileCache(JNIEnv* env, jclass)
{
  return reinterpret_cast<jlong>(new UICommon::GameFileCache());
}

JNIEXPORT void JNICALL Java_org_dolphinemu_dolphinemu_model_GameFileCache_finalize(JNIEnv* env,
                                                                                   jobject obj)
{
  delete GetPointer(env, obj);
}

JNIEXPORT jint JNICALL Java_org_dolphinemu_dolphinemu_model_GameFileCache_getSize(JNIEnv* env,
                                                                                  jobject obj)
{
  return static_cast<jint>(GetPointer(env, obj)->GetSize());
}

JNIEXPORT jobjectArray JNICALL
Java_org_dolphinemu_dolphinemu_model_GameFileCache_getAllGames(JNIEnv* env, jobject obj)
{
  const UICommon::GameFileCache* ptr = GetPointer(env, obj);
  const jobjectArray array =
      env->NewObjectArray(static_cast<jsize>(ptr->GetSize()), IDCache::GetGameFileClass(), nullptr);
  jsize i = 0;
  GetPointer(env, obj)->ForEach([env, array, &i](const auto& game_file) {
    env->SetObjectArrayElement(array, i++, GameFileToJava(env, game_file));
  });
  return array;
}

JNIEXPORT jobject JNICALL Java_org_dolphinemu_dolphinemu_model_GameFileCache_addOrGet(JNIEnv* env,
                                                                                      jobject obj,
                                                                                      jstring path)
{
  bool cache_changed = false;
  return GameFileToJava(env, GetPointer(env, obj)->AddOrGet(GetJString(env, path), &cache_changed));
}

JNIEXPORT jboolean JNICALL Java_org_dolphinemu_dolphinemu_model_GameFileCache_update(
    JNIEnv* env, jobject obj, jobjectArray folder_paths, jboolean recursive_scan)
{
  return GetPointer(env, obj)->Update(
      UICommon::FindAllGamePaths(JStringArrayToVector(env, folder_paths), recursive_scan));
}

JNIEXPORT jboolean JNICALL
Java_org_dolphinemu_dolphinemu_model_GameFileCache_updateAdditionalMetadata(JNIEnv* env,
                                                                            jobject obj)
{
  return GetPointer(env, obj)->UpdateAdditionalMetadata();
}

JNIEXPORT jboolean JNICALL Java_org_dolphinemu_dolphinemu_model_GameFileCache_load(JNIEnv* env,
                                                                                   jobject obj)
{
  return GetPointer(env, obj)->Load();
}

JNIEXPORT jboolean JNICALL Java_org_dolphinemu_dolphinemu_model_GameFileCache_save(JNIEnv* env,
                                                                                   jobject obj)
{
  return GetPointer(env, obj)->Save();
}

#ifdef __cplusplus
}
#endif
