package whomm.hsqlformat.hive.parse;

import java.util.List;

/**
 * This interface defines the functions needed by the walkers and dispatchers.
 * These are implemented by the node of the graph that needs to be walked.
 */
public interface Node {

  /**
   * Gets the vector of children nodes. This is used in the graph walker
   * algorithms.
   * 
   * @return List<? extends Node>
   */
  List<? extends Node> getChildren();

  /**
   * Gets the name of the node. This is used in the rule dispatchers.
   * 
   * @return String
   */
  String getName();
}