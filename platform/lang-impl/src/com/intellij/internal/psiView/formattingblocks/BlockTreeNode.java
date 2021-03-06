package com.intellij.internal.psiView.formattingblocks;

import com.intellij.formatting.Block;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;

import java.awt.*;
public class BlockTreeNode extends SimpleNode {
  private final Block myBlock;

  public BlockTreeNode(Block block, BlockTreeNode parent) {
    super(parent);
    myBlock = block;
  }

  public Block getBlock() {
    return myBlock;
  }

  @Override
  public BlockTreeNode[] getChildren() {
    return ContainerUtil.map2Array(myBlock.getSubBlocks(), BlockTreeNode.class, new Function<Block, BlockTreeNode>() {
      @Override
      public BlockTreeNode fun(Block block) {
        return new BlockTreeNode(block, BlockTreeNode.this);
      }
    });
  }

  @Override
  protected void update(PresentationData presentation) {
    presentation.addText(new String(myBlock.getClass().getSimpleName()), SimpleTextAttributes.REGULAR_ATTRIBUTES);

    if (myBlock.getIndent() != null) {
      presentation.addText(" " + String.valueOf(myBlock.getIndent()).replaceAll("[<>]", " "), SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
    else {
      presentation.addText(" Indent: null", SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
    if (myBlock.getAlignment() != null) {
      presentation
        .addText(" " + String.valueOf(myBlock.getAlignment()), new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, Color.darkGray));
    }
    if (myBlock.getWrap() != null) {
      presentation
        .addText(" " + String.valueOf(myBlock.getWrap()), new SimpleTextAttributes(SimpleTextAttributes.STYLE_ITALIC, Color.BLUE));
    }
  }


  @Override
  public Object[] getEqualityObjects() {
    return new Object[]{myBlock};
  }

  @Override
  public boolean isAlwaysLeaf() {
    return myBlock.isLeaf();
  }
}
