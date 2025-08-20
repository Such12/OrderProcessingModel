// hatchling
import java.io.*;
import java.util.*;

// ====================== Domain Model ======================

class Item {
    public String itemId;
    public int qty;

    public Item(String itemId, int qty) {
        this.itemId = itemId;
        this.qty = qty;
    }
}

class Order {
    String orderId;
    String customerId;
    List<Item> items;
    double totalAmount;
    String status;
    List<Event> eventHistory;

    public Order(String orderId, String customerId, List<Item> items, double totalAmount) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.items = items;
        this.totalAmount = totalAmount;
        this.status = "PENDING";
        this.eventHistory = new ArrayList<>();
    }

    public void updateStatus(String newStatus) {
        this.status = newStatus;
    }

    @Override
    public String toString() {
        return "Order{" +
                "orderId='" + orderId + '\'' +
                ", customerId='" + customerId + '\'' +
                ", status='" + status + '\'' +
                ", totalAmount=" + totalAmount +
                '}';  
    }
}

// ====================== Event Base Class ====================== 

abstract class Event {
    public String eventId;
    public String timestamp;
    public String eventType;

    public abstract void process(EventProcessor processor);
}

// ====================== Event Subclasses ======================

class OrderCreatedEvent extends Event {
    public String orderId;
    public String customerId;
    public List<Item> items;
    public double totalAmount;

    @Override
    public void process(EventProcessor processor) {
        processor.handle(this);
    }
}

class PaymentReceivedEvent extends Event {
    public String orderId;
    public double amountPaid;

    @Override
    public void process(EventProcessor processor) {
        processor.handle(this);
    }
}

class ShippingScheduledEvent extends Event {
    public String orderId;
    public String shippingDate;

    @Override
    public void process(EventProcessor processor) {
        processor.handle(this);
    }
}

class OrderCancelledEvent extends Event {
    public String orderId;
    public String reason;

    @Override
    public void process(EventProcessor processor) {
        processor.handle(this);
    }
}

// ====================== Observer Pattern ======================

interface Observer {
    void notify(Order order, Event event);
}

class LoggerObserver implements Observer {
    @Override
    public void notify(Order order, Event event) {
        System.out.println("[Logger] Event processed: " + event.eventType +
                " for Order " + order.orderId + " | Current Status: " + order.status);
    }
}

class AlertObserver implements Observer {
    @Override
    public void notify(Order order, Event event) {
        if (event instanceof OrderCancelledEvent || order.status.equals("SHIPPED")) {
            System.out.println("[ALERT] Sending alert for Order " + order.orderId +
                    ": Status changed to " + order.status);
        }
    }
}

// ====================== Event Processor ======================

class EventProcessor {
    private Map<String, Order> orders = new HashMap<>();
    private List<Observer> observers = new ArrayList<>();

    public void registerObserver(Observer observer) {
        observers.add(observer);
    }

    public void processEvent(Event event) {
        event.process(this);
    }

    public void handle(OrderCreatedEvent e) {
        Order order = new Order(e.orderId, e.customerId, e.items, e.totalAmount);
        orders.put(e.orderId, order);
        order.eventHistory.add(e);
        notifyObservers(order, e);
    }

    public void handle(PaymentReceivedEvent e) {
        Order order = orders.get(e.orderId);
        if (order != null) {
            if (e.amountPaid >= order.totalAmount) {
                order.updateStatus("PAID");
            } else {
                order.updateStatus("PARTIALLY_PAID");
            }
            order.eventHistory.add(e);
            notifyObservers(order, e);
        }
    }

    public void handle(ShippingScheduledEvent e) {
        Order order = orders.get(e.orderId);
        if (order != null) {
            order.updateStatus("SHIPPED");
            order.eventHistory.add(e);
            notifyObservers(order, e);
        }
    }

    public void handle(OrderCancelledEvent e) {
        Order order = orders.get(e.orderId);
        if (order != null) {
            order.updateStatus("CANCELLED");
            order.eventHistory.add(e);
            notifyObservers(order, e);
        }
    }

    private void notifyObservers(Order order, Event event) {
        for (Observer obs : observers) {
            obs.notify(order, event);
        }
    }
}

// ====================== Event Loader ======================

class EventLoader {

    // Simple JSON parser using string search (not production-grade, just enough for this prompt)
    private String getValue(String json, String key) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start == -1) return null;
        start += pattern.length();
        // skip spaces and quotes
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\"')) {
            start++;
        }
        int end = start;
        boolean insideQuotes = json.charAt(start - 1) == '\"';
        if (insideQuotes) {
            end = json.indexOf("\"", start);
            return json.substring(start, end);
        } else {
            while (end < json.length() && ",}]".indexOf(json.charAt(end)) == -1) {
                end++;
            }
            return json.substring(start, end).trim();
        }
    }

    public List<Event> loadEvents(String filePath) throws IOException {
        List<Event> events = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                Event event = parseEvent(line);
                if (event != null) {
                    events.add(event);
                }
            }
        }
        return events;
    }

    private Event parseEvent(String json) {
        String type = getValue(json, "eventType");
        if (type == null) return null;

        switch (type) {
            case "OrderCreated":
                OrderCreatedEvent oce = new OrderCreatedEvent();
                oce.eventType = type;
                oce.eventId = getValue(json, "eventId");
                oce.timestamp = getValue(json, "timestamp");
                oce.orderId = getValue(json, "orderId");
                oce.customerId = getValue(json, "customerId");
                oce.totalAmount = Double.parseDouble(getValue(json, "totalAmount"));
                // crude item parsing: assume one item for simplicity
                String itemId = getValue(json, "itemId");
                String qtyStr = getValue(json, "qty");
                oce.items = new ArrayList<>();
                if (itemId != null && qtyStr != null) {
                    oce.items.add(new Item(itemId, Integer.parseInt(qtyStr)));
                }
                return oce;

            case "PaymentReceived":
                PaymentReceivedEvent pre = new PaymentReceivedEvent();
                pre.eventType = type;
                pre.eventId = getValue(json, "eventId");
                pre.timestamp = getValue(json, "timestamp");
                pre.orderId = getValue(json, "orderId");
                pre.amountPaid = Double.parseDouble(getValue(json, "amountPaid"));
                return pre;

            case "ShippingScheduled":
                ShippingScheduledEvent sse = new ShippingScheduledEvent();
                sse.eventType = type;
                sse.eventId = getValue(json, "eventId");
                sse.timestamp = getValue(json, "timestamp");
                sse.orderId = getValue(json, "orderId");
                sse.shippingDate = getValue(json, "shippingDate");
                return sse;

            case "OrderCancelled":
                OrderCancelledEvent oce2 = new OrderCancelledEvent();
                oce2.eventType = type;
                oce2.eventId = getValue(json, "eventId");
                oce2.timestamp = getValue(json, "timestamp");
                oce2.orderId = getValue(json, "orderId");
                oce2.reason = getValue(json, "reason");
                return oce2;

            default:
                System.out.println("[WARN] Unsupported event type: " + type);
                return null;
        }
    }
}

// ====================== Demo / Main ======================

public class OrderProcessingSystem {
    public static void main(String[] args) throws Exception {
        EventLoader loader = new EventLoader();
        EventProcessor processor = new EventProcessor();

        // Register observers
        processor.registerObserver(new LoggerObserver());
        processor.registerObserver(new AlertObserver());

        // Load events from file
        List<Event> events = loader.loadEvents("events.txt");

        // Process events
        for (Event event : events) {
            processor.processEvent(event);
        }
    }
}
