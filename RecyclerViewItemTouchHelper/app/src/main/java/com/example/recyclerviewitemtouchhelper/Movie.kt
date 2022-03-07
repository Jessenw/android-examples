package com.example.recyclerviewitemtouchhelper

data class Movie(
    val title: String
) {
    companion object {
        val movies = arrayListOf(
            Movie("Interstellar"),
            Movie("The Godfather"),
            Movie("Pulp Fiction"),
            Movie("Shrek"),
            Movie("Misery"),
            Movie("Chicken Run"),
            Movie("Ferris Bueller's Day Off"),
            Movie("The Breakfast Club"),
            Movie("Ratatouille"),
            Movie("School of Rock"),
            Movie("Frozen"),
            Movie("The Shawshank Redemption"),
            Movie("Finding Nemo"),
            Movie("Spirited Away")
        )
    }
}
