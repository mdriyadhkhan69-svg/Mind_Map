package com.example.mindmap.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mindmap.data.NodeEntity
import com.example.mindmap.data.NodeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MindMapViewModel(private val repository: NodeRepository) : ViewModel() {

    val allNodes: StateFlow<List<NodeEntity>> = repository.getAllNodes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addRootDateNode(sectionId: Long, label: String, x: Float? = null, y: Float? = null) {
        viewModelScope.launch {
            val roots = allNodes.value.filter { it.parentId == null && it.sectionId == sectionId }
            val nextIndex = (roots.maxOfOrNull { it.orderIndex } ?: -1) + 1
            repository.insert(
                NodeEntity(
                    sectionId = sectionId,
                    parentId = null, label = label, orderIndex = nextIndex,
                    x = x ?: 200f,
                    y = y ?: 300f + nextIndex * 260f
                )
            )
        }
    }

    fun addChildNode(parent: NodeEntity, label: String) {
        viewModelScope.launch {
            val siblingCount = allNodes.value.count { it.parentId == parent.id }
            repository.insert(
                NodeEntity(
                    sectionId = parent.sectionId,
                    parentId = parent.id, label = label,
                    orderIndex = siblingCount,
                    x = parent.x + 360f, y = parent.y + siblingCount * 150f
                )
            )
            if (!parent.isExpanded) repository.update(parent.copy(isExpanded = true))
        }
    }

    fun addMediaChildNode(parent: NodeEntity, label: String, onCreated: (NodeEntity) -> Unit) {
        viewModelScope.launch {
            val siblingCount = allNodes.value.count { it.parentId == parent.id }
            val child = NodeEntity(
                sectionId = parent.sectionId,
                parentId = parent.id,
                label = label,
                orderIndex = siblingCount,
                x = parent.x + 360f,
                y = parent.y + siblingCount * 150f
            )
            val childId = repository.insert(child)
            if (!parent.isExpanded) repository.update(parent.copy(isExpanded = true))
            onCreated(child.copy(id = childId))
        }
    }

    fun pasteSubtree(
        targetParent: NodeEntity,
        sourceRootId: Long,
        sourceNodes: List<NodeEntity>,
        includeText: Boolean,
        includeSourceBox: Boolean?
    ) {
        viewModelScope.launch {
            val sourceRoot = sourceNodes.firstOrNull { it.id == sourceRootId } ?: return@launch
            val childrenByParentId = sourceNodes.groupBy { it.parentId }
            val existingChildCount = allNodes.value.count { it.parentId == targetParent.id }
            val shouldIncludeSourceBox = includeSourceBox ?: (sourceRoot.parentId != null)

            suspend fun insertCopy(source: NodeEntity, parentId: Long, topLevelOrderIndex: Int? = null) {
                val relativeX = source.x - sourceRoot.x
                val relativeY = source.y - sourceRoot.y
                val sourceChildren = childrenByParentId[source.id].orEmpty()
                val copy = source.copy(
                    id = 0,
                    sectionId = targetParent.sectionId,
                    parentId = parentId,
                    label = if (includeText) source.label else "",
                    orderIndex = topLevelOrderIndex ?: source.orderIndex,
                    x = targetParent.x + if (shouldIncludeSourceBox) 300f + relativeX else relativeX,
                    y = targetParent.y + existingChildCount * 130f + relativeY,
                    isExpanded = sourceChildren.isNotEmpty()
                )
                val insertedId = repository.insert(copy)
                sourceChildren.sortedBy { it.orderIndex }.forEach { child ->
                    insertCopy(child, insertedId)
                }
            }

            if (!shouldIncludeSourceBox) {
                childrenByParentId[sourceRoot.id].orEmpty()
                    .sortedBy { it.orderIndex }
                    .forEachIndexed { index, child ->
                        insertCopy(child, targetParent.id, existingChildCount + index)
                    }
            } else {
                insertCopy(sourceRoot, targetParent.id, existingChildCount)
            }
            repository.update(targetParent.copy(isExpanded = true))
        }
    }

    fun updateLabel(node: NodeEntity, newLabel: String) {
        viewModelScope.launch { repository.update(node.copy(label = newLabel)) }
    }

    fun toggleDone(node: NodeEntity) {
        viewModelScope.launch { repository.update(node.copy(isDone = !node.isDone)) }
    }

    fun deleteNode(node: NodeEntity) {
        viewModelScope.launch { repository.delete(node) }
    }

    fun updatePosition(node: NodeEntity, x: Float, y: Float) {
        viewModelScope.launch { repository.update(node.copy(x = x, y = y)) }
    }

    fun updatePositions(nodes: List<NodeEntity>) {
        viewModelScope.launch { nodes.forEach { repository.update(it) } }
    }
    fun updateColor(node: NodeEntity, colorArgb: Long?) {
        viewModelScope.launch { repository.update(node.copy(colorArgb = colorArgb)) }
    }
    fun updateBoxStyle(
        node: NodeEntity,
        colorArgb: Long?,
        textColorArgb: Long?,
        widthScale: Float,
        heightScale: Float
    ) {
        viewModelScope.launch {
            repository.update(
                node.copy(
                    colorArgb = colorArgb,
                    textColorArgb = textColorArgb,
                    widthScale = widthScale.coerceIn(0.65f, 2.2f),
                    heightScale = heightScale.coerceIn(0.65f, 2.2f)
                )
            )
        }
    }
    fun updateTextStyle(
        node: NodeEntity,
        label: String,
        textSizeSp: Float,
        textWeight: Int,
        textColorArgb: Long?
    ) {
        viewModelScope.launch {
            repository.update(
                node.copy(
                    label = label,
                    textSizeSp = textSizeSp.coerceIn(10f, 34f),
                    textWeight = textWeight.coerceIn(100, 1200),
                    textColorArgb = textColorArgb
                )
            )
        }
    }
    fun toggleExpand(node: NodeEntity, allowMultipleRoots: Boolean) {
        viewModelScope.launch {
            if (node.parentId == null && !allowMultipleRoots) {
                val newState = !node.isExpanded
                allNodes.value.filter { it.parentId == null && it.sectionId == node.sectionId }.forEach { root ->
                    repository.update(root.copy(isExpanded = if (root.id == node.id) newState else false))
                }
            } else {
                repository.update(node.copy(isExpanded = !node.isExpanded))
            }
        }
    }

    fun collapseAllRoots(sectionId: Long) {
        viewModelScope.launch {
            allNodes.value.filter {
                it.parentId == null && it.isExpanded && it.sectionId == sectionId
            }.forEach {
                repository.update(it.copy(isExpanded = false))
            }
        }
    }

    fun expandAncestors(nodeId: Long) {
        viewModelScope.launch {
            val nodesById = allNodes.value.associateBy { it.id }
            var parentId = nodesById[nodeId]?.parentId
            while (parentId != null) {
                val parent = nodesById[parentId] ?: break
                if (!parent.isExpanded) repository.update(parent.copy(isExpanded = true))
                parentId = parent.parentId
            }
        }
    }
    fun updateConnectorStyle(node: NodeEntity, colorArgb: Long, strokeWidth: Float) {
        viewModelScope.launch {
            repository.update(
                node.copy(
                    connectorColorArgb = colorArgb,
                    connectorStrokeWidth = strokeWidth.coerceIn(1f, 16f),
                    isConnectorHidden = false
                )
            )
        }
    }

    fun hideConnector(node: NodeEntity) {
        viewModelScope.launch { repository.update(node.copy(isConnectorHidden = true)) }
    }

    fun resetConnectorStyle(node: NodeEntity) {
        viewModelScope.launch {
            repository.update(
                node.copy(
                    connectorColorArgb = null,
                    connectorStrokeWidth = 3f,
                    isConnectorHidden = false
                )
            )
        }
    }
}

class MindMapViewModelFactory(private val repository: NodeRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return MindMapViewModel(repository) as T
    }
}
