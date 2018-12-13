package com.wavefront.dropwizard.metrics;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.wavefront.sdk.common.Pair;
import com.wavefront.sdk.common.annotation.NonNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * A taggable metric name.
 *
 * @author Han Zhang (zhanghan@vmware.com).
 */
public class TaggedMetricName {
  private final String group;
  private final String name;

  @NonNull
  private final Map<String, String> tags;

  /**
   * A simple metric that would be concatenated when reported, e.g. "jvm", "name" would become
   * jvm.name.
   *
   * @param group Prefix of the metric.
   * @param name  The name of the metric.
   */
  public TaggedMetricName(String group, String name) {
    this(group, name, new String[0]);
  }

  public TaggedMetricName(String group, String name, String... tagAndValues) {
    this(group, name, makeTags(tagAndValues));
  }

  public TaggedMetricName(String group, String name, Map<String, String> tags) {
    this(group, name, makeTags(tags));
  }

  public TaggedMetricName(String group, String name, Pair<String, String>... tags) {
    if (group == null) {
      throw new IllegalArgumentException("Group needs to be specified");
    }
    if (name == null) {
      throw new IllegalArgumentException("Name needs to be specified");
    }
    this.group = group;
    this.name = name;
    Map<String, String> tagMap = new TreeMap<>();
    for (Pair<String, String> tag : tags) {
      if (tag != null && tag._1 != null && tag._2 != null) {
        tagMap.put(tag._1, tag._2);
      }
    }
    this.tags = Collections.unmodifiableMap(tagMap);
  }

  public String getGroup() {
    return group;
  }

  public String getName() {
    return name;
  }

  public Map<String, String> getTags() {
    return tags;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    TaggedMetricName that = (TaggedMetricName) o;

    return getGroup().equals(that.getGroup())
        && getName().equals(that.getName())
        && getTags().equals(that.getTags());
  }

  @Override
  public int hashCode() {
    int result = 1;
    result = 31 * result + getGroup().hashCode();
    result = 31 * result + getName().hashCode();
    result = 31 * result + getTags().hashCode();
    return result;
  }

  /**
   * Serialize to a string.
   *
   * @return the serialized string.
   */
  public String encode() {
    StringBuilder sb = new StringBuilder(getGroup()).append('|').append(getName());
    for (Map.Entry<String, String> entry : tags.entrySet()) {
      sb.append('|').append(entry.getKey()).append('|').append(entry.getValue());
    }
    return sb.toString();
  }

  /**
   * Deserialize the string representation of {@link TaggedMetricName} to the object itself.
   *
   * @param s The string representation of the taggable metric name.
   * @return the deserialized {@link TaggedMetricName}.
   */
  public static TaggedMetricName decode(String s) {
    String[] parts = s.split("\\|");
    if (parts.length == 0) {
      throw new IllegalArgumentException("Invalid metric name");
    } else if (parts.length == 1) {
      return new TaggedMetricName("", parts[0]);
    } else if (parts.length == 2) {
      return new TaggedMetricName(parts[0], parts[1]);
    } else {
      return new TaggedMetricName(parts[0], parts[1], Arrays.copyOfRange(parts, 2, parts.length));
    }
  }

  /**
   * Return the {@link Gauge} registered under the provided {@link TaggedMetricName}; or create and
   * register a new {@link Gauge} using the provided MetricSupplier if none is registered.
   *
   * @param registry          The metric registry to register to.
   * @param taggedMetricName  The taggable metric name.
   * @param supplier          The MetricSupplier that is used to manufacture the {@link Gauge}.
   * @return a new or pre-existing {@link Gauge}.
   */
  public static Gauge gauge(MetricRegistry registry, TaggedMetricName taggedMetricName,
                            final MetricRegistry.MetricSupplier<Gauge> supplier) {
    return registry.gauge(taggedMetricName.encode(), supplier);
  }

  /**
   * Return the {@link Counter} registered under the provided {@link TaggedMetricName}; or create
   * and register a new {@link Counter} if none is registered.
   *
   * @param registry          The metric registry to register to.
   * @param taggedMetricName  The taggable metric name.
   * @return a new or pre-existing {@link Counter}.
   */
  public static Counter counter(MetricRegistry registry, TaggedMetricName taggedMetricName) {
    return registry.counter(taggedMetricName.encode());
  }

  /**
   * Return the {@link Histogram} registered under the provided {@link TaggedMetricName}; or create
   * and register a new {@link Histogram} if none is registered.
   *
   * @param registry          The metric registry to register to.
   * @param taggedMetricName  The taggable metric name.
   * @return a new or pre-existing {@link Histogram}.
   */
  public static Histogram histogram(MetricRegistry registry, TaggedMetricName taggedMetricName) {
    return registry.histogram(taggedMetricName.encode());
  }

  /**
   * Return the {@link Histogram} registered under the provided {@link TaggedMetricName}; or create
   * and register a new {@link Histogram} using the provided MetricSupplier if none is registered.
   *
   * @param registry          The metric registry to register to.
   * @param taggedMetricName  The taggable metric name.
   * @param supplier          The MetricSupplier that is used to manufacture the {@link Histogram}.
   * @return a new or pre-existing {@link Histogram}.
   */
  public static Histogram histogram(MetricRegistry registry, TaggedMetricName taggedMetricName,
                                    MetricRegistry.MetricSupplier<Histogram> supplier) {
    return registry.histogram(taggedMetricName.encode(), supplier);
  }

  /**
   * Return the {@link Meter} registered under the provided {@link TaggedMetricName}; or create
   * and register a new {@link Meter} if none is registered.
   *
   * @param registry          The metric registry to register to.
   * @param taggedMetricName  The taggable metric name.
   * @return a new or pre-existing {@link Meter}.
   */
  public static Meter meter(MetricRegistry registry, TaggedMetricName taggedMetricName) {
    return registry.meter(taggedMetricName.encode());
  }

  /**
   * Return the {@link Timer} registered under the provided {@link TaggedMetricName}; or create
   * and register a new {@link Timer} if none is registered.
   *
   * @param registry          The metric registry to register to.
   * @param taggedMetricName  The taggable metric name.
   * @return a new or pre-existing {@link Timer}.
   */
  public static Timer timer(MetricRegistry registry, TaggedMetricName taggedMetricName) {
    return registry.timer(taggedMetricName.encode());
  }

  /**
   * Removes the metric with the given name.
   *
   * @param registry          The metric registry to remove from.
   * @param taggedMetricName  The taggable metric name.
   * @return whether or not the metric was removed.
   */
  public static boolean remove(MetricRegistry registry, TaggedMetricName taggedMetricName) {
    return registry.remove(taggedMetricName.encode());
  }

  private static Pair<String, String>[] makeTags(Map<String, String> tags) {
    if (tags == null) {
      throw new IllegalArgumentException("Tags needs to be specified");
    }
    @SuppressWarnings("unchecked")
    Pair<String, String>[] toReturn = new Pair[tags.size()];
    int i = 0;
    for (Map.Entry<String, String> entry : tags.entrySet()) {
      toReturn[i] = new Pair<>(entry.getKey(), entry.getValue());
      i++;
    }
    return toReturn;
  }

  private static Pair<String, String>[] makeTags(String... tagAndValues) {
    if ((tagAndValues.length & 1) != 0) {
      throw new IllegalArgumentException("Must have even number of tag values");
    }
    @SuppressWarnings("unchecked")
    Pair<String, String>[] toReturn = new Pair[tagAndValues.length / 2];
    for (int i = 0; i < tagAndValues.length; i += 2) {
      String tag = tagAndValues[i];
      String value = tagAndValues[i + 1];
      if (tag != null && value != null) {
        toReturn[i / 2] = new Pair<>(tag, value);
      }
    }
    return toReturn;
  }
}
