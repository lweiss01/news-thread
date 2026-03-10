import com.newsthread.app.domain.similarity.EntityExtractor

fun main() {
    val e = EntityExtractor()
    println(e.extractEntities("Trump sends second aircraft carrier to Gulf amid Iran threats - Axios"))
    println(e.extractEntities("US Spy Plane, Drone Detected Near Iranian Border"))
}
