package com.rabbitmq.concourse;

import com.rabbitmq.concourse.GithubReleaseDeleteResource.Input;
import com.rabbitmq.concourse.GithubReleaseDeleteResource.Params;
import com.rabbitmq.concourse.GithubReleaseDeleteResource.Release;
import com.rabbitmq.concourse.GithubReleaseDeleteResource.Source;
import java.lang.reflect.Field;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

public class NativeImageFeature implements Feature {

  @Override
  public void beforeAnalysis(Feature.BeforeAnalysisAccess access) {
    Class<?>[] classes = new Class<?>[] {Input.class, Params.class, Source.class, Release.class};
    RuntimeReflection.registerForReflectiveInstantiation(classes);
    for (Class<?> clazz : classes) {
      for (Field field : clazz.getDeclaredFields()) {
        RuntimeReflection.register(field);
      }
    }
  }
}
