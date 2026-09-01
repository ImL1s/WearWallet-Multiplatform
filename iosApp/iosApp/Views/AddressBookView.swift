import SwiftUI

struct Contact: Identifiable, Codable {
    let id: UUID
    var name: String
    var address: String
    
    init(id: UUID = UUID(), name: String, address: String) {
        self.id = id
        self.name = name
        self.address = address
    }
}

class AddressBookViewModel: ObservableObject {
    @Published var contacts: [Contact] = []
    
    // Key for UserDefaults
    private let storageKey = "AddressBookContacts"
    
    init() {
        loadContacts()
    }
    
    func addContact(name: String, address: String) {
        let newContact = Contact(name: name, address: address)
        contacts.append(newContact)
        saveContacts()
        syncToWatch()
    }
    
    func deleteContact(at offsets: IndexSet) {
        contacts.remove(atOffsets: offsets)
        saveContacts()
        syncToWatch()
    }
    
    private func saveContacts() {
        if let encoded = try? JSONEncoder().encode(contacts) {
            UserDefaults.standard.set(encoded, forKey: storageKey)
        }
    }
    
    private func loadContacts() {
        if let data = UserDefaults.standard.data(forKey: storageKey),
           let decoded = try? JSONDecoder().decode([Contact].self, from: data) {
            contacts = decoded
        }
    }
    
    private func syncToWatch() {
        // Prepare data for WatchConnectivity
        let contactList = contacts.map { ["name": $0.name, "address": $0.address] }
        let message: [String: Any] = [
            "type": "address_book_sync",
            "timestamp": Date().timeIntervalSince1970,
            "contacts": contactList
        ]
        
        WatchConnectivityManager.shared.sendMessage(message)
    }
}

struct AddressBookView: View {
    @StateObject private var viewModel = AddressBookViewModel()
    @State private var showingAddSheet = false
    
    var body: some View {
        List {
            ForEach(viewModel.contacts) { contact in
                VStack(alignment: .leading) {
                    Text(contact.name)
                        .font(.headline)
                    Text(contact.address)
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                        .truncationMode(.middle)
                }
            }
            .onDelete(perform: viewModel.deleteContact)
        }
        .navigationTitle("address_book")
        .toolbar {
            Button(action: { showingAddSheet = true }) {
                Image(systemName: "plus")
            }
        }
        .sheet(isPresented: $showingAddSheet) {
            AddContactView(viewModel: viewModel)
        }
    }
}

struct AddContactView: View {
    @ObservedObject var viewModel: AddressBookViewModel
    @Environment(\.presentationMode) var presentationMode
    
    @State private var name = ""
    @State private var address = ""
    
    var body: some View {
        NavigationView {
            Form {
                Section(header: Text("contact_info")) {
                    TextField("contact_name", text: $name)
                    TextField("contact_address", text: $address)
                }
            }
            .navigationTitle("add_contact")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("cancel") {
                        presentationMode.wrappedValue.dismiss()
                    }
                }
                
                ToolbarItem(placement: .confirmationAction) {
                    Button("save") {
                        viewModel.addContact(name: name, address: address)
                        presentationMode.wrappedValue.dismiss()
                    }
                    .disabled(name.isEmpty || address.isEmpty)
                }
            }
        }
    }
}
