package info.nightscout.androidaps.plugins.configBuilder

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import androidx.annotation.StringRes
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.PreferencesActivity
import info.nightscout.androidaps.events.EventRebuildTabs
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.PasswordProtection
import info.nightscout.androidaps.utils.extensions.plusAssign
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.toVisibility
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.configbuilder_fragment.*
import java.util.*
import javax.inject.Inject

class ConfigBuilderFragment : DaggerFragment() {
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var configBuilderPlugin: ConfigBuilderPlugin
    @Inject lateinit var fabricPrivacy: FabricPrivacy

    private var disposable: CompositeDisposable = CompositeDisposable()
    private val pluginViewHolders = ArrayList<PluginViewHolder>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.configbuilder_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (PasswordProtection.isLocked("settings_password"))
            configbuilder_main_layout.visibility = View.GONE
        else
            unlock.visibility = View.GONE

        unlock.setOnClickListener {
            PasswordProtection.QueryPassword(context, R.string.settings_password, "settings_password", {
                configbuilder_main_layout.visibility = View.VISIBLE
                unlock.visibility = View.GONE
            }, null)
        }
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventConfigBuilderUpdateGui::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                for (pluginViewHolder in pluginViewHolders) pluginViewHolder.update()
            }, { fabricPrivacy.logException(it) })
        updateGUI()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    @Synchronized
    private fun updateGUI() {
        configbuilder_categories.removeAllViews()
        createViewsForPlugins(R.string.configbuilder_profile, R.string.configbuilder_profile_description, PluginType.PROFILE, MainApp.getSpecificPluginsVisibleInListByInterface(ProfileInterface::class.java, PluginType.PROFILE))
        createViewsForPlugins(R.string.configbuilder_insulin, R.string.configbuilder_insulin_description, PluginType.INSULIN, MainApp.getSpecificPluginsVisibleInListByInterface(InsulinInterface::class.java, PluginType.INSULIN))
        createViewsForPlugins(R.string.configbuilder_bgsource, R.string.configbuilder_bgsource_description, PluginType.BGSOURCE, MainApp.getSpecificPluginsVisibleInListByInterface(BgSourceInterface::class.java, PluginType.BGSOURCE))
        createViewsForPlugins(R.string.configbuilder_pump, R.string.configbuilder_pump_description, PluginType.PUMP, MainApp.getSpecificPluginsVisibleInList(PluginType.PUMP))
        createViewsForPlugins(R.string.configbuilder_sensitivity, R.string.configbuilder_sensitivity_description, PluginType.SENSITIVITY, MainApp.getSpecificPluginsVisibleInListByInterface(SensitivityInterface::class.java, PluginType.SENSITIVITY))
        createViewsForPlugins(R.string.configbuilder_aps, R.string.configbuilder_aps_description, PluginType.APS, MainApp.getSpecificPluginsVisibleInList(PluginType.APS))
        createViewsForPlugins(R.string.configbuilder_loop, R.string.configbuilder_loop_description, PluginType.LOOP, MainApp.getSpecificPluginsVisibleInList(PluginType.LOOP))
        createViewsForPlugins(R.string.constraints, R.string.configbuilder_constraints_description, PluginType.CONSTRAINTS, MainApp.getSpecificPluginsVisibleInListByInterface(ConstraintsInterface::class.java, PluginType.CONSTRAINTS))
        createViewsForPlugins(R.string.configbuilder_treatments, R.string.configbuilder_treatments_description, PluginType.TREATMENT, MainApp.getSpecificPluginsVisibleInList(PluginType.TREATMENT))
        createViewsForPlugins(R.string.configbuilder_general, R.string.configbuilder_general_description, PluginType.GENERAL, MainApp.getSpecificPluginsVisibleInList(PluginType.GENERAL))
    }

    private fun createViewsForPlugins(@StringRes title: Int, @StringRes description: Int, pluginType: PluginType, plugins: List<PluginBase>) {
        if (plugins.isEmpty()) return
        @Suppress("InflateParams")
        val parent = layoutInflater.inflate(R.layout.configbuilder_single_category, null) as LinearLayout
        (parent.findViewById<View>(R.id.category_title) as TextView).text = resourceHelper.gs(title)
        (parent.findViewById<View>(R.id.category_description) as TextView).text = resourceHelper.gs(description)
        val pluginContainer = parent.findViewById<LinearLayout>(R.id.category_plugins)
        for (plugin in plugins) {
            val pluginViewHolder = PluginViewHolder(this, pluginType, plugin)
            pluginContainer.addView(pluginViewHolder.baseView)
            pluginViewHolders.add(pluginViewHolder)
        }
        configbuilder_categories.addView(parent)
    }

    inner class PluginViewHolder internal constructor(private val fragment: ConfigBuilderFragment,
                                                      private val pluginType: PluginType,
                                                      private val plugin: PluginBase) {

        @Suppress("InflateParams")
        val baseView: LinearLayout = fragment.layoutInflater.inflate(R.layout.configbuilder_single_plugin, null) as LinearLayout
        private val enabledExclusive: RadioButton
        private val enabledInclusive: CheckBox
        private val pluginName: TextView
        private val pluginDescription: TextView
        private val pluginPreferences: ImageButton
        private val pluginVisibility: CheckBox

        init {
            enabledExclusive = baseView.findViewById(R.id.plugin_enabled_exclusive)
            enabledInclusive = baseView.findViewById(R.id.plugin_enabled_inclusive)
            pluginName = baseView.findViewById(R.id.plugin_name)
            pluginDescription = baseView.findViewById(R.id.plugin_description)
            pluginPreferences = baseView.findViewById(R.id.plugin_preferences)
            pluginVisibility = baseView.findViewById(R.id.plugin_visibility)

            pluginVisibility.setOnClickListener {
                plugin.setFragmentVisible(pluginType, pluginVisibility.isChecked)
                configBuilderPlugin.storeSettings("CheckedCheckboxVisible")
                rxBus.send(EventRebuildTabs())
                configBuilderPlugin.logPluginStatus()
            }

            enabledExclusive.setOnClickListener {
                plugin.switchAllowed(if (enabledExclusive.visibility == View.VISIBLE) enabledExclusive.isChecked else enabledInclusive.isChecked, fragment.activity, pluginType)
            }
            enabledInclusive.setOnClickListener {
                plugin.switchAllowed(if (enabledExclusive.visibility == View.VISIBLE) enabledExclusive.isChecked else enabledInclusive.isChecked, fragment.activity, pluginType)
            }

            pluginPreferences.setOnClickListener {
                PasswordProtection.QueryPassword(fragment.context, R.string.settings_password, "settings_password", {
                    val i = Intent(fragment.context, PreferencesActivity::class.java)
                    i.putExtra("id", plugin.preferencesId)
                    fragment.startActivity(i)
                }, null)
            }
            update()
        }

        fun update() {
            enabledExclusive.visibility = areMultipleSelectionsAllowed(pluginType).not().toVisibility()
            enabledInclusive.visibility = areMultipleSelectionsAllowed(pluginType).toVisibility()
            enabledExclusive.isChecked = plugin.isEnabled(pluginType)
            enabledInclusive.isChecked = plugin.isEnabled(pluginType)
            enabledInclusive.isEnabled = !plugin.pluginDescription.alwaysEnabled
            enabledExclusive.isEnabled = !plugin.pluginDescription.alwaysEnabled
            pluginName.text = plugin.name
            if (plugin.description == null)
                pluginDescription.visibility = View.GONE
            else {
                pluginDescription.visibility = View.VISIBLE
                pluginDescription.text = plugin.description
            }
            pluginPreferences.visibility = if (plugin.preferencesId == -1 || !plugin.isEnabled(pluginType)) View.INVISIBLE else View.VISIBLE
            pluginVisibility.visibility = plugin.hasFragment().toVisibility()
            pluginVisibility.isEnabled = !(plugin.pluginDescription.neverVisible || plugin.pluginDescription.alwaysVisible) && plugin.isEnabled(pluginType)
            pluginVisibility.isChecked = plugin.isFragmentVisible()
        }

        private fun areMultipleSelectionsAllowed(type: PluginType): Boolean {
            return type == PluginType.GENERAL || type == PluginType.CONSTRAINTS || type == PluginType.LOOP
        }

    }

}
