package org.sui.cli.runConfigurations.producers.aptos

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.psi.PsiElement
import org.sui.cli.runConfigurations.aptos.AptosTransactionConfigurationType
import org.sui.cli.runConfigurations.aptos.view.ViewCommandConfiguration
import org.sui.cli.runConfigurations.aptos.view.ViewCommandConfigurationFactory
import org.sui.cli.runConfigurations.aptos.view.ViewCommandConfigurationHandler
import org.sui.cli.runConfigurations.producers.SuiCommandLineFromContext

class ViewCommandConfigurationProducer : FunctionCallConfigurationProducerBase<ViewCommandConfiguration>() {
    override fun getConfigurationFactory(): ConfigurationFactory =
        ViewCommandConfigurationFactory(AptosTransactionConfigurationType.getInstance())

    override fun fromLocation(location: PsiElement, climbUp: Boolean): SuiCommandLineFromContext? =
        ViewCommandConfigurationHandler().configurationFromLocation(location)
}
