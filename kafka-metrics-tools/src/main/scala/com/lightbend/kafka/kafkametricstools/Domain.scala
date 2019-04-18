package com.lightbend.kafka.kafkametricstools

import com.lightbend.kafka.kafkametricstools.Domain.Measurements.Measurement

import scala.collection.mutable

object Domain {
  object Measurements {

    sealed trait Measurement {
      def offset: Long
      def timestamp: Long
      def addMeasurement(single: Single): Double
      def offsetLag(lastOffset: Long): Long
    }

    final case class Single(offset: Long, timestamp: Long) extends Measurement {
      def addMeasurement(b: Single): Double = Double(this, b)
      def offsetLag(lastOffset: Long): Long = lastOffset - offset
    }

    final case class Double(a: Single, b: Single) extends Measurement {
      val offset: Long = b.offset
      val timestamp: Long = b.timestamp
      def addMeasurement(c: Single): Double = Double(b, c)
      def offsetLag(lastOffset: Long): Long = {
        if (lastOffset <= 0 || b.offset > lastOffset) 0
        else {
          lastOffset - b.offset
        }
      }

      def timeLag(lastOffset: Long): scala.Double = {
        val lagMs: Long =
          if (lastOffset <= b.offset || b.offset - a.offset == 0) 0
          else {
            val now = b.timestamp
            // linear extrapolation, solve for the x intercept given y (lastOffset), slope (dy/dx), and two points (a, b)
            val dx = (b.timestamp - a.timestamp).toDouble
            val dy = (b.offset - a.offset).toDouble
            val Px = b.timestamp
            val Dy = (b.offset - lastOffset).toDouble

            val lagPx = Px - (now + (Dy * (dx / dy)))

            //println(s"lagPx = $lagPx = $Px - ($now + ($Dy*($dx/$dy)))")

            lagPx.toLong
          }

        lagMs.toDouble / 1000
      }
    }

  }

  final case class TopicPartition(topic: String, partition: Int)
  final case class GroupTopicPartition(group: ConsumerGroup, topicPartition: TopicPartition)

  final case class ConsumerGroup(id: String, isSimpleGroup: Boolean, state: String, members: List[ConsumerGroupMember])
  final case class ConsumerGroupMember(clientId: String, consumerId: String, host: String, partitions: Set[TopicPartition])

  type GroupOffsets = Map[GroupTopicPartition, Measurement]

  object GroupOffsets {
    def apply(): GroupOffsets = Map.empty[GroupTopicPartition, Measurement]
  }

  type PartitionOffsets = Map[TopicPartition, Measurement]

  object PartitionOffsets {
    def apply(): PartitionOffsets = Map.empty[TopicPartition, Measurement]
  }

  class TopicPartitionTable private(var tables: Map[TopicPartition, LookupTable.Table]) {
    def apply(tp: TopicPartition): LookupTable.Table = {
      tables = tables.updated(tp, tables.getOrElse(tp, LookupTable.Table(20)))
      tables(tp)
    }

    def all: Map[TopicPartition, LookupTable.Table] = tables
  }

  object TopicPartitionTable {
    def apply(tables: Map[TopicPartition, LookupTable.Table] = Map.empty[TopicPartition, LookupTable.Table]): TopicPartitionTable =
      new TopicPartitionTable(tables)
  }
}
