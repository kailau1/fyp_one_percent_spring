# One Percent - Spring Boot Backend (University Final Year Project)

This is the backend API for *One Percent*, a self-development app designed to support students in building better habits and engaging in reflective journaling. The backend handles user authentication, journaling, habit tracking, and AI-generated feedback through integration with a Large Language Model (LLM).

## Features

- User registration and login with JWT authentication
- Journal entry creation, retrieval, and editing
- AI feedback generation using OpenAI's API
- Journal summarisation and theme detection using embeddings and cosine similarity
- Habit tracking
- MongoDB integration for storing user data, journals, and habits

## Technologies Used

- Java 17
- Spring Boot
- MongoDB
- JWT (Json Web Token)
- OpenAI API
- Maven

## Project Structure

- `controllers/` – API endpoint definitions
- `services/` – Core business logic for journals, users, and habits
- `models/` – Domain models and DTOs
- `config/` – Application configuration (security, CORS, OpenAI client)
- `repositories/` – MongoDB access interfaces

## Getting Started

### Prerequisites

- Java 17
- MongoDB
- Maven

### Running the Application

1. Clone the respository:
   
```
git clone https://github.com/kailau1/fyp_one_percent_spring
```

2. Configure environment variables

- MongoDB connection string
- JWT secret key
- OpenAI API key

3. Build and run the project:

```
./mvnw spring-boot:run
```

OR

Open the project folder in your preferred IDE with Java.

The application will start on http://localhost:8084

## Related Resositories

[React Native Frontend](https://github.com/kailau1/fyp_one_percent)
