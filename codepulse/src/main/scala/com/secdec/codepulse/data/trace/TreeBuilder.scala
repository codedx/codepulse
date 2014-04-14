package com.secdec.codepulse.data.trace

import com.fasterxml.jackson.core.{ JsonFactory, JsonGenerator }

import com.secdec.codepulse.data.bytecode.CodeTreeNodeKind

case class TreeNode(data: TreeNodeData, children: List[TreeNode])

case class PackageTreeNode(id: Option[Int], kind: CodeTreeNodeKind, label: String, methodCount: Int, traced: Option[Boolean], children: List[PackageTreeNode])

/** Builds/projects treemap and package tree data as JSON for client.
  * TODO: manage lifetime of cached data internally
  *
  * @author robertf
  */
class TreeBuilder(treeNodeData: TreeNodeDataAccess) {
	lazy val (roots, nodes) = {
		val roots = List.newBuilder[Int]
		val nodes = Map.newBuilder[Int, TreeNodeData]
		val children = collection.mutable.HashMap.empty[Int, collection.mutable.Builder[Int, List[Int]]]

		def childrenFor(id: Int) = children.getOrElseUpdate(id, List.newBuilder[Int])

		treeNodeData foreach { data =>
			nodes += data.id -> data
			(data.parentId match {
				case Some(parent) => childrenFor(parent)
				case None => roots
			}) += data.id
		}

		val nodeMap = nodes.result

		def buildNode(id: Int): TreeNode = {
			val children = childrenFor(id).result.map(buildNode)
			TreeNode(nodeMap(id), children)
		}

		(roots.result.map(buildNode), nodeMap)
	}

	lazy val packageTree = {
		// build up a package tree with the relevant data

		/** A node is eligible for a self node if it is a package node that has at least one
		  * package child and one non-package child (class/method).
		  */
		def isEligibleForSelfNode(node: TreeNode) = {
			node.data.kind == CodeTreeNodeKind.Pkg &&
				node.children.exists {
					case TreeNode(data, _) if data.kind == CodeTreeNodeKind.Pkg => true
					case _ => false
				} &&
				node.children.exists {
					case TreeNode(data, _) if data.kind == CodeTreeNodeKind.Cls || data.kind == CodeTreeNodeKind.Mth => true
					case _ => false
				}
		}

		def countMethods(node: TreeNode): Int = {
			(node.children map {
				case TreeNode(data, _) if data.kind == CodeTreeNodeKind.Mth => 1
				case node => countMethods(node)
			}).sum
		}

		def transform(node: TreeNode): PackageTreeNode = {
			// we don't want methods
			lazy val filteredChildren = node.children.filterNot { _.data.kind == CodeTreeNodeKind.Mth }

			if (isEligibleForSelfNode(node)) {
				// split the node children depending on where they belong
				val (selfChildren, children) = filteredChildren.partition {
					case TreeNode(data, _) if data.kind == CodeTreeNodeKind.Cls || data.kind == CodeTreeNodeKind.Mth => true
					case _ => false
				}

				// build the self node
				val selfNode = PackageTreeNode(Some(node.data.id), CodeTreeNodeKind.Pkg, "<self>", selfChildren.map(countMethods).sum, node.data.traced, selfChildren.map(transform))
				PackageTreeNode(None, node.data.kind, node.data.label, countMethods(node), node.data.traced, selfNode :: children.map(transform))
			} else {
				PackageTreeNode(Some(node.data.id), node.data.kind, node.data.label, countMethods(node), node.data.traced, filteredChildren.map(transform))
			}
		}

		roots.map(transform)
	}
}