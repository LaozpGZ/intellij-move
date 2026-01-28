package org.sui.cli.toolwindow

import com.intellij.diagnostic.PluginException
import com.intellij.execution.Location
import com.intellij.execution.PsiLocation
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext

import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities
import javax.swing.tree.DefaultMutableTreeNode

class MoveEntrypointMouseAdapter : MouseAdapter() {

    override fun mouseClicked(e: MouseEvent) {
        if (e.clickCount < 2 || !SwingUtilities.isLeftMouseButton(e)) return

        val tree = e.source as? MoveProjectsTree ?: return
        val node = tree.selectionModel.selectionPath
            ?.lastPathComponent as? DefaultMutableTreeNode ?: return
        val userObject = node.userObject
        val function = when (userObject) {
            is MoveProjectsTreeStructure.MoveSimpleNode.Entrypoint -> userObject.function
            is MoveProjectsTreeStructure.MoveSimpleNode.View -> userObject.function
            else -> return
        }
        val functionLocation =
            try {
                PsiLocation.fromPsiElement(function) ?: return
            } catch (e: PluginException) {
                // TODO: figure out why this exception is raised
                return
            }
        val dataContext =
            SimpleDataContext.getSimpleContext(Location.DATA_KEY, functionLocation)
        val context = ConfigurationContext.getFromContext(dataContext, ActionPlaces.TOOLWINDOW_CONTENT)

        val executor = DefaultRunExecutor.getRunExecutorInstance()

        // Get or create run configuration
        val configuration = context.findExisting() ?: context.getConfiguration()
        if (configuration != null) {
            ExecutionUtil.runConfiguration(configuration, executor)
        }
    }
}
