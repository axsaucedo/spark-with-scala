package io.e_x.spark

import org.apache.spark._
import org.apache.spark.SparkContext._
import org.apache.log4j._
import scala.io.Source
import java.nio.charset.CodingErrorAction
import scala.io.Codec
import scala.math.sqrt

object MovieSimilarities {
  
  /** Load up a Map of movie IDs to movie names. */
  def loadMovieNames() : (Map[Int, String], Map[Int, List[Int]]) = {
    
    // Handle character encoding issues:
    implicit val codec = Codec("UTF-8")
    codec.onMalformedInput(CodingErrorAction.REPLACE)
    codec.onUnmappableCharacter(CodingErrorAction.REPLACE)

    // Create a Map of Ints to Strings, and populate it from u.item.
    var movieNames:Map[Int, String] = Map()
    
    var genres: Map[Int, List[Int]] = Map()
    
     val lines = Source.fromFile("../ml-100k/u.item").getLines()
     for (line <- lines) {
       var fields = line.split('|')
       
       if (fields.length > 1) {
        movieNames += (fields(0).toInt -> fields(1))
        
        val genre = new Array[Int](19)
        val start: Int = 5
        
        for(i <- start to fields.length-1) {
          val actual = i - start
          genre(actual) = fields(i).toInt
        }
        genres += (fields(0).toInt -> genre.toList)
       }
     }
    
    
     return (movieNames, genres)
  }
  
  type MovieRating = (Int, Double, List[Int])
  type UserRatingPair = (Int, (MovieRating, MovieRating))
  def makePairs(userRatings:UserRatingPair) = {
    val movieRating1 = userRatings._2._1
    val movieRating2 = userRatings._2._2
    
    val movie1 = movieRating1._1
    val rating1 = movieRating1._2
    val genre1 = movieRating1._3
    val movie2 = movieRating2._1
    val rating2 = movieRating2._2
    val genre2 = movieRating2._3
    
    ((movie1, movie2), ((rating1, rating2), (genre1, genre2)))
  }
  
  def filterDuplicates(userRatings:UserRatingPair):Boolean = {
    val movieRating1 = userRatings._2._1
    val movieRating2 = userRatings._2._2
    
    val movie1 = movieRating1._1
    val movie2 = movieRating2._1
    
    return movie1 < movie2
  }
  
  type RatingPair = ((Double, Double), (List[Int], List[Int]))
  type RatingPairs = Iterable[RatingPair]
  
  def computeCosineSimilarity(ratingPairs:RatingPairs): (Double, Int) = {
    var numPairs:Int = 0
    var sum_xx:Double = 0.0
    var sum_yy:Double = 0.0
    var sum_xy:Double = 0.0
    var sum_genre:Int = 0
    
    for (pair <- ratingPairs) {
      val ratingX = pair._1._1
      val ratingY = pair._1._2
      
      sum_xx += ratingX * ratingX
      sum_yy += ratingY * ratingY
      sum_xy += ratingX * ratingY
      
      val genreX = pair._2._1
      val genreY = pair._2._2
      
      if (genreX == genreY) {
        sum_genre += 1
      }
      
      numPairs += 1
    }
    
    val numerator:Double = sum_xy
    val denominator = sqrt(sum_xx) * sqrt(sum_yy)
    
    var score:Double = 0.0
    var scoreRating: Double = 0.0
    var scoreGenre: Double = 0.0
    
    var weightRating: Double = 0.5
    var weightGenre: Double = 0.5
    
    if (denominator != 0) {
      scoreRating = numerator / denominator
      scoreGenre = sum_genre / numPairs
      
      score = (weightRating*scoreRating) + (weightGenre*scoreGenre)
    }
    
    return (score, numPairs)
  }
  
  /** Our main function where the action happens */
  def main(args: Array[String]) {
    
    // Set the log level to only print errors
    Logger.getLogger("org").setLevel(Level.ERROR)
    
     // Create a SparkContext using every core of the local machine
    val sc = new SparkContext("local[*]", "MovieSimilarities")
    
    println("\nLoading movie names...")
    val namesGenres = loadMovieNames()
    val nameDict = namesGenres._1
    val genres = namesGenres._2
    
    val data = sc.textFile("../ml-100k/u.data")

    // Map ratings to key / value pairs: user ID => movie ID, rating
    val ratings = data.map(l => l.split("\t")).map(l => (l(0).toInt, (l(1).toInt, l(2).toDouble, genres(l(0).toInt))))
    
    val filteredRatings = ratings.filter(_._2._2 > 3)
    
    // Emit every movie rated together by the same user.
    // Self-join to find every combination.
    val joinedRatings = filteredRatings.join(filteredRatings)   
    
    // At this point our RDD consists of userID => ((movieID, rating), (movieID, rating))

    // Filter out duplicate pairs
    val uniqueJoinedRatings = joinedRatings.filter(filterDuplicates)

    // Now key by (movie1, movie2) pairs.
    val moviePairs = uniqueJoinedRatings.map(makePairs)

    // We now have (movie1, movie2) => (rating1, rating2)
    // Now collect all ratings for each movie pair and compute similarity
    val moviePairRatings = moviePairs.groupByKey()

    // We now have (movie1, movie2) = > (rating1, rating2), (rating1, rating2) ...
    // Can now compute similarities.
    val moviePairSimilarities = moviePairRatings.mapValues(computeCosineSimilarity).cache()
    
    //Save the results if desired
    //val sorted = moviePairSimilarities.sortByKey()
    //sorted.saveAsTextFile("movie-sims")
    
    // Extract similarities for the movie we care about that are "good".
    
    if (args.length > 0) {
      val scoreThreshold = 0.97
      val coOccurenceThreshold = 50.0
      
      val movieID:Int = args(0).toInt
      
      // Filter for movies with this sim that are "good" as defined by
      // our quality thresholds above     
      
      val filteredResults = moviePairSimilarities.filter( x =>
        {
          val pair = x._1
          val sim = x._2
          (pair._1 == movieID || pair._2 == movieID) && sim._1 > scoreThreshold && sim._2 > coOccurenceThreshold
        }
      )
        
      // Sort by quality score.
      val results = filteredResults.map( x => (x._2, x._1)).sortByKey(false).take(10)
      
      println("\nTop 10 similar movies for " + nameDict(movieID))
      for (result <- results) {
        val sim = result._1
        val pair = result._2
        // Display the similarity result that isn't the movie we're looking at
        var similarMovieID = pair._1
        if (similarMovieID == movieID) {
          similarMovieID = pair._2
        }
        println(nameDict(similarMovieID) + "\tscore: " + sim._1 + "\tstrength: " + sim._2)
      }
    }
  }
}