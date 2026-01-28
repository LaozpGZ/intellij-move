package org.sui.cli.runConfigurations.producers.aptos

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.psi.PsiElement
import org.sui.cli.runConfigurations.aptos.AptosTransactionConfigurationType
import org.sui.cli.runConfigurations.aptos.run.RunCommandConfiguration
import org.sui.cli.runConfigurations.aptos.run.RunCommandConfigurationFactory
import org.sui.cli.runConfigurations.aptos.run.RunCommandConfigurationHandler
import org.sui.cli.runConfigurations.producers.SuiCommandLineFromContext

class RunCommandConfigurationProducer : FunctionCallConfigurationProducerBase<RunCommandConfiguration>() {
    override fun getConfigurationFactory(): ConfigurationFactory =
        RunCommandConfigurationFactory(AptosTransactionConfigurationType.getInstance())

    override fun fromLocation(location: PsiElement, climbUp: Boolean): SuiCommandLineFromContext? =
        RunCommandConfigurationHandler().configurationFromLocation(location)
}
