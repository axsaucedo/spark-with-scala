package io.e_x.spark

import org.apache.spark._
import org.apache.spark.SparkContext._
import org.apache.log4j._

object CustomerSpend {
  
  def parseLine(line: String) = {
    val fields = line.split(",")
    val customerID = fields(0).toInt
    val spent = fields(2).toFloat
    (customerID, spent)
  }
  
  def main(args: Array[String]) = {
    
    Logger.getLogger("org").setLevel(Level.ERROR)
    
    val sc = new SparkContext("local[*]", "CustomerSpend")
    
    val lines = sc.textFile("../customer-orders.csv")
    
    val rdd = lines.map(parseLine)
    
    val totalSpent = rdd.reduceByKey( (x,y) => x + y )
    
    val totalByCustomer = totalSpent.sortBy(_._2)
    
    val results = totalByCustomer.collect() 
    
    results.foreach(println)    
  }
  
}