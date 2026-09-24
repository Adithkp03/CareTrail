from datetime import date

from pydantic import BaseModel, Field, model_validator


class SignupRequest(BaseModel):
    name: str = Field(min_length=1, max_length=120)
    phone: str = Field(min_length=6, max_length=32)
    password: str = Field(min_length=6, max_length=128)
    language: str = Field(default="en", pattern="^[a-z]{2}$")


class LoginRequest(BaseModel):
    phone: str
    password: str


class JourneyCreateRequest(BaseModel):
    lmp: date | None = None
    edd: date | None = None
    template_id: str = "antenatal"

    @model_validator(mode="after")
    def need_a_date(self):
        if self.lmp is None and self.edd is None:
            raise ValueError("Provide lmp (last menstrual period) or edd (expected delivery date)")
        return self


class MilestoneCompleteRequest(BaseModel):
    completed_at: date | None = None  # defaults to today
    notes: str = ""


class MilestoneScheduleRequest(BaseModel):
    scheduled_date: date


class DocumentCreateRequest(BaseModel):
    journey_id: str
    milestone_id: str | None = None
    filename: str = Field(min_length=1, max_length=200)
    content_type: str = "application/pdf"
    storage_path: str = ""
    language: str | None = None


class SignOffCreateRequest(BaseModel):
    journey_id: str
    milestone_id: str | None = None
    observation_id: str | None = None
    note: str = ""


class ConfirmValue(BaseModel):
    code: str
    value: float
    unit: str = ""
    observed_on: date | None = None


class DocumentConfirmRequest(BaseModel):
    milestone_id: str | None = None
    values: list[ConfirmValue] = Field(min_length=1, max_length=50)
