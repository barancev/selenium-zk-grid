package ru.stqa.selenium.zkgrid.common;

public class SlotAllocationResponse {

  private Status status;
  private String message;
  private SlotInfo slotInfo;

  public SlotAllocationResponse() {
  }

  public SlotAllocationResponse(Status status, SlotInfo slotInfo) {
    this(status, slotInfo, null);
  }

  public SlotAllocationResponse(Status status, SlotInfo slotInfo, String message) {
    this.status = status;
    this.slotInfo = slotInfo;
    this.message = message;
  }

  public enum Status { OK, NO_MATCHING_SLOT, NO_FREE_SLOT }

  public Status getStatus() {
    return status;
  }

  public String getMessage() {
    return message;
  }

  public SlotInfo getSlotInfo() {
    return slotInfo;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public void setSlotInfo(SlotInfo slotInfo) {
    this.slotInfo = slotInfo;
  }

}
