from pydantic import BaseModel
from typing import List

class QuestionSchema(BaseModel):
    questionText: str
    answers: List[str]
    correctIndex: int

class QuizResultSchema(BaseModel):
    roomCode: str
    questions: List[QuestionSchema]