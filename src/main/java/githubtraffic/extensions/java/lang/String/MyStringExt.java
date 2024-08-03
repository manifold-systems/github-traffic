package githubtraffic.extensions.java.lang.String;

import manifold.ext.rt.api.Extension;
import manifold.ext.rt.api.This;
import manifold.rt.api.util.ManStringUtil;

import java.lang.String;
import java.util.Arrays;
import java.util.stream.Stream;

@Extension
public class MyStringExt {
  public static String repeat(@This String thiz, int times) {
    return ManStringUtil.repeat(thiz, times);
  }
  public static Stream<String> lines(@This String thiz) {
    return Arrays.stream(thiz.split("\\R"));
  }
}