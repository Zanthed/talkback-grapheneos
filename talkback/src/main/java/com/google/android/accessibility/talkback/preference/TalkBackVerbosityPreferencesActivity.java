/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.android.accessibility.talkback.preference;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentFactory;
import android.text.TextUtils;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.SwitchPreference;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.utils.VerbosityPreferences;
import com.google.android.accessibility.utils.PreferencesActivity;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.ArrayList;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Activity used to set TalkBack's verbosity preferences.
 *
 * <p>Allow flexibility for multiple customizable presets, in the future.
 */
public class TalkBackVerbosityPreferencesActivity extends PreferencesActivity {

  private static final String TAG = "TBVerbosityPrefActivity";

  @Override
  public void onCreate(Bundle savedInstanceState) {
    // Sets FragmentFactory when VerbosityPrefFragment uses a non-default constructor to get
    // ContainerId. This results framework sometimes restores VerbosityPrefFragment by default
    // constructor only.
    getSupportFragmentManager()
        .setFragmentFactory(new TalkBackVerbosityFragmentFactory(getContainerId()));
    super.onCreate(savedInstanceState);
  }

  @Override
  protected PreferenceFragmentCompat createPreferenceFragment() {
    return new VerbosityPrefFragment(getContainerId());
  }

  /**
   * A {@code FragmentFactory} which creates TalkBackVerbosityPreferencesActivity uses a non-default
   * constructor to ensure that this constructor is called when the fragment is re-instantiated.
   */
  private static final class TalkBackVerbosityFragmentFactory extends FragmentFactory {
    private final int containerId;

    public TalkBackVerbosityFragmentFactory(int containerId) {
      super();
      this.containerId = containerId;
    }

    @NonNull
    @Override
    public Fragment instantiate(@NonNull ClassLoader classLoader, @NonNull String className) {
      Class<? extends Fragment> clazz = loadFragmentClass(classLoader, className);
      if (clazz == VerbosityPrefFragment.class) {
        return new VerbosityPrefFragment(containerId);
      } else {
        return super.instantiate(classLoader, className);
      }
    }
  }

  /** Panel holding a set of verbosity preferences. Recreated when preset value changes. */
  public static class VerbosityPrefFragment extends TalkbackBaseFragment {

    // Member data
    private SharedPreferences preferences;
    private String presetValue; // String identifier for selected preset.
    private Context context;
    private int containerId;

    public VerbosityPrefFragment() {
      super(R.xml.verbosity_preferences);
    }

    VerbosityPrefFragment(int containerId) {
      super(R.xml.verbosity_preferences);
      this.containerId = containerId;
    }

    @Override
    public void onAttach(Context context) {
      super.onAttach(context);
      this.context = context;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
      super.onCreatePreferences(savedInstanceState, rootKey);

      preferences = SharedPreferencesUtils.getSharedPreferences(context);
      presetValue =
          SharedPreferencesUtils.getStringPref(
              preferences,
              getResources(),
              R.string.pref_verbosity_preset_key,
              R.string.pref_verbosity_preset_value_default);

      ArrayList<Preference> detailedPrefs = collectDetailedPreferences();
      copyPresetsToUi(detailedPrefs); // Cheap, just reading preferences.

      // Disable default preset preference details.
      if (presetValue.equals(getString(R.string.pref_verbosity_preset_value_high))
          || presetValue.equals(getString(R.string.pref_verbosity_preset_value_low))) {
        disablePreferenceDetails(detailedPrefs);
      }

      // Attach listeners after preset values are copied to active, so that copying preset does
      // not invoke preference-change listener.
      attachPreferenceListeners();
    }

    /** Collects all preset-controlled preferences. */
    private ArrayList<Preference> collectDetailedPreferences() {
      ArrayList<Preference> detailedPrefs = new ArrayList<Preference>();
      PreferenceGroup prefGroup =
          (PreferenceGroup) findPreference(R.string.pref_verbosity_category_preset_settings_key);
      if (prefGroup == null) {
        return detailedPrefs;
      }

      // For each preference... collect
      for (int p = 0; p < prefGroup.getPreferenceCount(); p++) {
        Preference preference = prefGroup.getPreference(p);
        if (preference != null) {
          detailedPrefs.add(preference);
        }
      }
      return detailedPrefs;
    }

    private void copyPresetsToUi(ArrayList<Preference> detailedPrefs) {
      // For each detailed preference...
      for (Preference preference : detailedPrefs) {
        // Change active key to preset key.
        String key = preference.getKey();
        String keyForPreset = VerbosityPreferences.toPresetPrefKey(presetValue, key);
        preference.setKey(keyForPreset);

        // Retrieve preset preference value and update UI element.
        if (preference instanceof SwitchPreference) {
          SwitchPreference prefSwitch = (SwitchPreference) preference;
          boolean value =
              VerbosityPreferences.getPreferencePresetBool(
                  preferences,
                  getResources(),
                  presetValue,
                  key,
                  getDefaultValueForSwitchPreferences(key));
          prefSwitch.setChecked(value);
        } else if (preference instanceof ListPreference) {
          ListPreference prefList = (ListPreference) preference;
          String value =
              VerbosityPreferences.getPreferencePresetString(
                  preferences, getResources(), presetValue, key, null);
          if (value != null) {
            prefList.setValue(value);
          }
        } else {
          LogUtils.e(TAG, "Unhandled preference type %s", preference.getClass().getSimpleName());
        }
      }
    }

    private void disablePreferenceDetails(ArrayList<Preference> detailedPrefs) {
      // For each detailed preference... disable preference.
      for (Preference preference : detailedPrefs) {
        preference.setEnabled(false);
      }
    }

    // Returns the default value for the given key.
    private boolean getDefaultValueForSwitchPreferences(String key) {
      if (key.equals(getResources().getString(R.string.pref_screenoff_key))) {
        return getResources().getBoolean(R.bool.pref_screenoff_default);
      } else if (key.equals(getResources().getString(R.string.pref_a11y_hints_key))) {
        return getResources().getBoolean(R.bool.pref_a11y_hints_default);
      } else if (key.equals(getResources().getString(R.string.pref_intonation_key))) {
        return getResources().getBoolean(R.bool.pref_intonation_default);
      } else if (key.equals(getResources().getString(R.string.pref_phonetic_letters_key))) {
        return getResources().getBoolean(R.bool.pref_phonetic_letters_default);
      } else if (key.equals(getResources().getString(R.string.pref_speak_roles_key))) {
        return getResources().getBoolean(R.bool.pref_speak_roles_default);
      } else if (key.equals(
          getResources().getString(R.string.pref_speak_container_element_positions_key))) {
        return getResources().getBoolean(R.bool.pref_speak_container_element_positions_default);
      } else if (key.equals(
          getResources().getString(R.string.pref_verbose_scroll_announcement_key))) {
        return getResources().getBoolean(R.bool.pref_verbose_scroll_announcement_default);
      } else if (key.equals(getResources().getString(R.string.pref_punctuation_key))) {
        return getResources().getBoolean(R.bool.pref_punctuation_default);
      }
      return true;
    }

    @Override
    public void onResume() {
      super.onResume();
      attachPreferenceListeners();
      String presetValueString =
          preferences.getString(
              getString(R.string.pref_verbosity_preset_key),
              getString(R.string.pref_verbosity_preset_value_default));
      replaceFragment(presetValueString);
    }

    private void attachPreferenceListeners() {
      preferences.registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);
    }

    @Override
    public void onPause() {
      super.onPause();
      preferences.unregisterOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);
    }

    /** Listener for preference changes. */
    private final OnSharedPreferenceChangeListener onSharedPreferenceChangeListener =
        new OnSharedPreferenceChangeListener() {
          @Override
          public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            if (!isAdded() || (getActivity() == null)) {
              LogUtils.w(
                  TAG,
                  "Fragment is not attached to activity, do not update verbosity setting page.");
              return;
            }
            // Handles ListPreference changed case and case where the preset is changed
            // using the selector and the fragment is visible.
            if (TextUtils.equals(key, getString(R.string.pref_verbosity_preset_key))) {
              String newValueString =
                  preferences.getString(
                      getString(R.string.pref_verbosity_preset_key),
                      getString(R.string.pref_verbosity_preset_value_default));

              replaceFragment(newValueString);

              // Announce new preset. If the verbosity is changed using the selector,
              // GestureController.changeVerbosity will also call this method. SpeechController
              // will then deduplicate the announcement event so only one is spoken.
              announcePresetChange(newValueString);
            } else if (TextUtils.equals(key, getString(R.string.pref_punctuation_key))) {
              SwitchPreference preference =
                  (SwitchPreference) findPreference(R.string.pref_punctuation_key);
              boolean punctuationOn =
                  prefs.getBoolean(
                      context.getString(R.string.pref_punctuation_key),
                      context.getResources().getBoolean(R.bool.pref_punctuation_default));

              if (preference != null) {
                preference.setChecked(punctuationOn);
              }
            }
          }
        };

    /** Replace preference fragment if the preset value has changed */
    private void replaceFragment(String newValueString) {
      if (TextUtils.equals(presetValue, newValueString)) {
        return;
      }
      VerbosityPrefFragment newFragment = new VerbosityPrefFragment(containerId);
      getFragmentManager().beginTransaction().replace(containerId, newFragment).commit();
    }

    private Preference findPreference(int keyId) {
      return getPreferenceScreen().findPreference(getString(keyId));
    }

    private void announcePresetChange(String newValueString) {
      String announcement = getVerbosityChangeAnnouncement(newValueString, context);
      if (announcement == null) {
        return;
      }
      PreferencesActivityUtils.announceText(announcement, context);
    }
  }

  /** Map preset value key to preset name. */
  public static String presetValueToName(String presetValueKey, Context context) {
    if (presetValueKey.equals(context.getString(R.string.pref_verbosity_preset_value_high))) {
      return context.getString(R.string.pref_verbosity_preset_entry_high);
    } else if (presetValueKey.equals(
        context.getString(R.string.pref_verbosity_preset_value_custom))) {
      return context.getString(R.string.pref_verbosity_preset_entry_custom);
    } else if (presetValueKey.equals(context.getString(R.string.pref_verbosity_preset_value_low))) {
      return context.getString(R.string.pref_verbosity_preset_entry_low);
    } else {
      return null;
    }
  }

  /** Returns announcement for the change of verbosity. */
  @Nullable
  public static String getVerbosityChangeAnnouncement(String presetValueKey, Context context) {
    String name = presetValueToName(presetValueKey, context);
    return TextUtils.isEmpty(name)
        ? null
        : String.format(
            context.getString(R.string.pref_verbosity_preset_change),
            presetValueToName(presetValueKey, context));
  }
}
