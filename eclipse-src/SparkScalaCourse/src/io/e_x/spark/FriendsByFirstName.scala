package io.e_x.spark

import org.apache.spark._
import org.apache.spark.SparkContext._
import org.apache.log4j._

object FriendsByFirstName {
  
  def parseLine(line: String) = {
    val fields = line.split(",")
    val firstName = fields(1).toString
    val numFriends = fields(3).toInt
    
    (firstName, numFriends)
  }
  
  def main(args: Array[String]) = {
    
    Logger.getLogger("org").setLevel(Level.ERROR)
    
    val sc = new SparkContext("local[*]", "FriendsByFirstName")
    
    val lines = sc.textFile("../fakefriends.csv")
    
    val rdd = lines.map(parseLine)
    
    val totalsByFirstName = rdd.mapValues(x => (x, 1))
                                .reduceByKey( (x,y) => (x._1 + y._1, x._2 + y._2) )

    val averagesByFirstName = totalsByFirstName.mapValues(x => x._1 / x._2)
    
    val results = averagesByFirstName.collect()
    
    results.sorted.foreach(println)
  }
  
}