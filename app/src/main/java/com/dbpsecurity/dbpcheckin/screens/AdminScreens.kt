package com.dbpsecurity.dbpcheckin.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.dbpsecurity.dbpcheckin.data.SupabaseClient
import com.dbpsecurity.dbpcheckin.models.Group
import com.dbpsecurity.dbpcheckin.models.Profile
import com.dbpsecurity.dbpcheckin.ui.theme.*
import com.dbpsecurity.dbpcheckin.ui.theme.Error as ErrorColor
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun AdminHomeScreen(navController: NavController) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Primary, Secondary),
                    startY = 0f,
                    endY = 1500f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            Surface(
                modifier = Modifier.size(80.dp).clip(CircleShape),
                color = White,
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "Admin",
                        modifier = Modifier.size(48.dp),
                        tint = Primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Admin Dashboard",
                style = MaterialTheme.typography.headlineMedium,
                color = White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Manage your organization",
                style = MaterialTheme.typography.bodyMedium,
                color = White.copy(alpha = 0.9f)
            )
            Spacer(modifier = Modifier.height(48.dp))
            Card(
                modifier = Modifier.fillMaxWidth().height(140.dp),
                onClick = { navController.navigate("admin_management") },
                colors = CardDefaults.cardColors(containerColor = White),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(70.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Primary.copy(alpha = 0.1f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = "Manage",
                                modifier = Modifier.size(40.dp),
                                tint = Primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Manage Groups",
                            style = MaterialTheme.typography.titleLarge,
                            color = Primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Add, edit, and organize employee groups",
                            style = MaterialTheme.typography.bodyMedium,
                            color = DarkGrey
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Card(
                modifier = Modifier.fillMaxWidth().height(140.dp),
                onClick = { navController.navigate("admin_profile") },
                colors = CardDefaults.cardColors(containerColor = White),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(70.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Accent.copy(alpha = 0.1f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.AccountCircle,
                                contentDescription = "Profile",
                                modifier = Modifier.size(40.dp),
                                tint = Accent
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "My Profile",
                            style = MaterialTheme.typography.titleLarge,
                            color = Accent,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "View and edit your account details",
                            style = MaterialTheme.typography.bodyMedium,
                            color = DarkGrey
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminManagementScreen(navController: NavController) {
    val context = LocalContext.current
    val supabase = SupabaseClient.client
    val scope = rememberCoroutineScope()

    var showAddGroupDialog by remember { mutableStateOf(false) }
    var showEditGroupDialog by remember { mutableStateOf(false) }
    var groupToEdit by remember { mutableStateOf<Group?>(null) }

    var newGroupName by remember { mutableStateOf("") }
    var newGroupLat by remember { mutableStateOf("") }
    var newGroupLong by remember { mutableStateOf("") }
    var newGroupRadius by remember { mutableStateOf("100.0") }
    var newGroupStartTime by remember { mutableStateOf("") }
    var newGroupEndTime by remember { mutableStateOf("") }
    var selectedMemberIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    var showTehsilSelectionDialog by remember { mutableStateOf(false) }
    var currentTehsilName by remember { mutableStateOf("") }
    var currentTehsilUsers by remember { mutableStateOf<List<Profile>>(emptyList()) }

    var editGroupName by remember { mutableStateOf("") }
    var editGroupLat by remember { mutableStateOf("") }
    var editGroupLong by remember { mutableStateOf("") }
    var editGroupRadius by remember { mutableStateOf("") }
    var editGroupStartTime by remember { mutableStateOf("") }
    var editGroupEndTime by remember { mutableStateOf("") }
    var editGroupMembers by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var editGroupSelectedNewMembers by remember { mutableStateOf<Set<String>>(emptySet()) }

    var groups by remember { mutableStateOf<List<Group>>(emptyList()) }
    var unassignedUsers by remember { mutableStateOf<List<Profile>>(emptyList()) }

    suspend fun fetchAllData() {
        try {
            groups = supabase.from("groups").select().decodeList()
            val allEmployees = supabase.from("profiles").select {
                filter {
                    eq("role", "employee")
                }
            }.decodeList<Profile>()

            unassignedUsers = allEmployees.filter { it.groupId == null }
        } catch (e: Exception) {
            Toast.makeText(context, "Error fetching data: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        fetchAllData()
        val channel = supabase.channel("db-changes")
        scope.launch {
            channel.postgresChangeFlow<PostgresAction>(schema = "public").collectLatest {
                fetchAllData()
            }
        }
        channel.subscribe()
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    newGroupName = ""
                    newGroupLat = ""
                    newGroupLong = ""
                    newGroupRadius = "100.0"
                    newGroupStartTime = ""
                    newGroupEndTime = ""
                    selectedMemberIds = emptySet()
                    showAddGroupDialog = true
                },
                containerColor = Accent,
                contentColor = White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Group")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Primary, Secondary),
                        startY = 0f,
                        endY = 2000f
                    )
                )
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = "Manage Groups",
                style = MaterialTheme.typography.headlineMedium,
                color = White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(groups) { group ->
                    GroupCard(
                        group = group,
                        onEditClick = {
                            groupToEdit = it
                            editGroupName = it.name
                            editGroupLat = it.latitude.toString()
                            editGroupLong = it.longitude.toString()
                            editGroupRadius = it.radius.toString()
                            editGroupStartTime = it.startTime
                            editGroupEndTime = it.endTime
                            editGroupSelectedNewMembers = emptySet()

                            scope.launch {
                                try {
                                    Log.d("AdminScreens", "Fetching members for group: ${it.id}")
                                    val members = supabase.from("profiles").select {
                                        filter { eq("group_id", it.id) }
                                    }.decodeList<Profile>()
                                    Log.d("AdminScreens", "Fetched ${members.size} members for group ${it.id}")
                                    editGroupMembers = members
                                    showEditGroupDialog = true
                                } catch (e: Exception) {
                                    Log.e("AdminScreens", "Error fetching group members", e)
                                    Toast.makeText(context, "Error fetching members: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onDeleteClick = {
                            scope.launch {
                                try {
                                    // Unassign all members of this group
                                    supabase.from("profiles").update(
                                        { "group_id" to null }
                                    ) {
                                        filter { eq("group_id", it.id) }
                                    }
                                    supabase.from("groups").delete { filter { eq("id", it.id) } }
                                    Toast.makeText(context, "Group '${it.name}' deleted", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error deleting group: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    if (showAddGroupDialog) {
        GroupDialog(
            title = "Create New Group",
            groupName = newGroupName, onGroupNameChange = { newGroupName = it },
            lat = newGroupLat, onLatChange = { newGroupLat = it },
            long = newGroupLong, onLongChange = { newGroupLong = it },
            radius = newGroupRadius, onRadiusChange = { newGroupRadius = it },
            startTime = newGroupStartTime, onStartTimeChange = { newGroupStartTime = it },
            endTime = newGroupEndTime, onEndTimeChange = { newGroupEndTime = it },
            availableUsers = unassignedUsers,
            selectedUserIds = selectedMemberIds,
            onUserSelectionChanged = { userId, isSelected ->
                selectedMemberIds = if (isSelected) selectedMemberIds + userId else selectedMemberIds - userId
            },
            onDismiss = { showAddGroupDialog = false },
            onConfirm = {
                if (newGroupName.isNotEmpty() && newGroupLat.isNotEmpty() && newGroupLong.isNotEmpty()) {
                    scope.launch {
                        try {
                            val newGroup = Group(
                                id = UUID.randomUUID().toString(),
                                name = newGroupName,
                                latitude = newGroupLat.toDouble(),
                                longitude = newGroupLong.toDouble(),
                                startTime = newGroupStartTime,
                                endTime = newGroupEndTime,
                                radius = newGroupRadius.toDoubleOrNull() ?: 100.0
                            )
                            Log.d("AdminScreens", "Creating group: ${newGroup.id}")
                            supabase.from("groups").insert(listOf(newGroup))

                            if (selectedMemberIds.isNotEmpty()) {
                                Log.d("AdminScreens", "Assigning members: $selectedMemberIds to group ${newGroup.id}")
                                try {
                                    var successCount = 0
                                    selectedMemberIds.forEach { memberId ->
                                        try {
                                            val updated = supabase.from("profiles").update({ "group_id" to newGroup.id }) {
                                                filter { eq("id", memberId) }
                                                select()
                                            }.decodeList<Profile>()

                                            if (updated.isNotEmpty()) {
                                                successCount++
                                                Log.d("AdminScreens", "Updated member $memberId. New group_id: ${updated[0].groupId}")
                                            } else {
                                                Log.e("AdminScreens", "Update returned empty for member $memberId. Trying RPC fallback...")
                                                try {
                                                    val params = mapOf("user_id" to memberId, "new_group_id" to newGroup.id)
                                                    supabase.postgrest.rpc("assign_user_to_group", params)
                                                    successCount++
                                                    Log.d("AdminScreens", "RPC update successful for member $memberId")
                                                } catch (rpcError: Exception) {
                                                    Log.e("AdminScreens", "RPC fallback failed for member $memberId", rpcError)
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e("AdminScreens", "Failed to update member $memberId", e)
                                        }
                                    }
                                    Log.d("AdminScreens", "Successfully assigned $successCount members to group ${newGroup.id}")
                                } catch (e: Exception) {
                                    Log.e("AdminScreens", "Failed to assign members loop", e)
                                    throw e
                                }
                            } else {
                                Log.d("AdminScreens", "No members selected for new group")
                            }
                            showAddGroupDialog = false
                            fetchAllData()
                        } catch (e: Exception) {
                            Log.e("AdminScreens", "Error creating group", e)
                            Toast.makeText(context, "Error creating group: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        )
    }

    if (showEditGroupDialog && groupToEdit != null) {
        val group = groupToEdit!!
        GroupDialog(
            isEdit = true,
            title = "Edit Group",
            groupName = editGroupName,
            onGroupNameChange = { editGroupName = it },
            lat = editGroupLat,
            onLatChange = { editGroupLat = it },
            long = editGroupLong,
            onLongChange = { editGroupLong = it },
            radius = editGroupRadius,
            onRadiusChange = { editGroupRadius = it },
            startTime = editGroupStartTime,
            onStartTimeChange = { editGroupStartTime = it },
            endTime = editGroupEndTime,
            onEndTimeChange = { editGroupEndTime = it },
            availableUsers = unassignedUsers,
            selectedUserIds = editGroupSelectedNewMembers,
            onUserSelectionChanged = { userId, isSelected ->
                editGroupSelectedNewMembers = if (isSelected) editGroupSelectedNewMembers + userId else editGroupSelectedNewMembers - userId
            },
            currentMembers = editGroupMembers,
            onRemoveMember = { memberId ->
                val memberToRemove = editGroupMembers.find { it.id == memberId }
                scope.launch {
                    try {
                        Log.d("AdminScreens", "Removing member $memberId from group")
                        val updated = supabase.from("profiles").update({ "group_id" to null }) {
                            filter { eq("id", memberId) }
                            select()
                        }.decodeList<Profile>()

                        if (updated.isNotEmpty()) {
                            Log.d("AdminScreens", "Member $memberId removed successfully (standard update)")
                            editGroupMembers = editGroupMembers.filter { it.id != memberId }
                            if (memberToRemove != null) {
                                unassignedUsers = unassignedUsers + memberToRemove.copy(groupId = null)
                            }
                            Toast.makeText(context, "Member removed", Toast.LENGTH_SHORT).show()
                        } else {
                            Log.e("AdminScreens", "Standard remove failed for $memberId. Trying RPC fallback...")
                            try {
                                val params = mapOf("user_id" to memberId, "new_group_id" to null)
                                supabase.postgrest.rpc("assign_user_to_group", params)
                                Log.d("AdminScreens", "RPC remove successful for member $memberId")
                                editGroupMembers = editGroupMembers.filter { it.id != memberId }
                                if (memberToRemove != null) {
                                    unassignedUsers = unassignedUsers + memberToRemove.copy(groupId = null)
                                }
                                Toast.makeText(context, "Member removed (RPC)", Toast.LENGTH_SHORT).show()
                            } catch (rpcError: Exception) {
                                Log.e("AdminScreens", "RPC remove failed for member $memberId", rpcError)
                                Toast.makeText(context, "Failed to remove member", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("AdminScreens", "Error removing member", e)
                        Toast.makeText(context, "Error removing member: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { showEditGroupDialog = false },
            onConfirm = {
                val lat = editGroupLat.toDoubleOrNull()
                val long = editGroupLong.toDoubleOrNull()
                val rad = editGroupRadius.toDoubleOrNull() ?: 100.0

                if (lat == null || long == null) {
                    Toast.makeText(context, "Invalid Latitude or Longitude", Toast.LENGTH_SHORT).show()
                } else {
                    scope.launch {
                        try {
                            Log.d("AdminScreens", "Updating group: ${group.id} with name=$editGroupName, lat=$lat, long=$long, radius=$rad")

                            // Use upsert to ensure it updates the existing record
                            val groupToUpdate = Group(
                                id = group.id,
                                name = editGroupName,
                                latitude = lat,
                                longitude = long,
                                startTime = editGroupStartTime,
                                endTime = editGroupEndTime,
                                radius = rad
                            )

                            val updatedGroup = supabase.from("groups").upsert(groupToUpdate) {
                                select()
                            }.decodeSingleOrNull<Group>()

                            if (updatedGroup != null) {
                                Log.d("AdminScreens", "Group updated successfully: $updatedGroup")
                                Toast.makeText(context, "Group updated successfully", Toast.LENGTH_SHORT).show()
                            } else {
                                Log.e("AdminScreens", "Group update returned null. ID: ${group.id}")
                                // Fallback: Try standard update if upsert returned null (unlikely but possible)
                                supabase.from("groups").update({
                                    "name" to editGroupName
                                    "latitude" to lat
                                    "longitude" to long
                                    "radius" to rad
                                    "start_time" to editGroupStartTime
                                    "end_time" to editGroupEndTime
                                }) {
                                    filter { eq("id", group.id) }
                                }
                                Toast.makeText(context, "Group update attempted (check list)", Toast.LENGTH_SHORT).show()
                            }

                            if (editGroupSelectedNewMembers.isNotEmpty()) {
                                Log.d("AdminScreens", "Assigning new members: $editGroupSelectedNewMembers to group ${group.id}")
                                try {
                                    var successCount = 0
                                    editGroupSelectedNewMembers.forEach { memberId ->
                                        try {
                                            val updated = supabase.from("profiles").update({ "group_id" to group.id }) {
                                                filter { eq("id", memberId) }
                                                select()
                                            }.decodeList<Profile>()

                                            if (updated.isNotEmpty()) {
                                                successCount++
                                                Log.d("AdminScreens", "Updated member $memberId. New group_id: ${updated[0].groupId}")
                                            } else {
                                                Log.e("AdminScreens", "Update returned empty for member $memberId. Trying RPC fallback...")
                                                try {
                                                    val params = mapOf("user_id" to memberId, "new_group_id" to group.id)
                                                    supabase.postgrest.rpc("assign_user_to_group", params)
                                                    successCount++
                                                    Log.d("AdminScreens", "RPC update successful for member $memberId")
                                                } catch (rpcError: Exception) {
                                                    Log.e("AdminScreens", "RPC fallback failed for member $memberId", rpcError)
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e("AdminScreens", "Failed to update member $memberId", e)
                                        }
                                    }
                                    Log.d("AdminScreens", "Successfully assigned $successCount new members to group ${group.id}")
                                } catch (e: Exception) {
                                    Log.e("AdminScreens", "Failed to assign new members loop", e)
                                    throw e
                                }
                            } else {
                                Log.d("AdminScreens", "No new members selected for update")
                            }
                            showEditGroupDialog = false
                            fetchAllData()
                        } catch (e: Exception) {
                            Log.e("AdminScreens", "Error saving changes", e)
                            Toast.makeText(context, "Error saving changes: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun GroupCard(group: Group, onEditClick: (Group) -> Unit, onDeleteClick: (Group) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onEditClick(group) },
        colors = CardDefaults.cardColors(containerColor = White.copy(alpha = 0.95f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = group.name, style = MaterialTheme.typography.titleMedium, color = Primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Time: ${group.startTime} - ${group.endTime}",
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkGrey
                    )
                }
                Row {
                    IconButton(
                        onClick = { onEditClick(group) },
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Primary.copy(alpha = 0.1f))
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Group", tint = Primary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { onDeleteClick(group) },
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(ErrorColor.copy(alpha = 0.1f))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Group", tint = ErrorColor)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDialog(
    isEdit: Boolean = false,
    title: String,
    groupName: String, onGroupNameChange: (String) -> Unit,
    lat: String, onLatChange: (String) -> Unit,
    long: String, onLongChange: (String) -> Unit,
    radius: String, onRadiusChange: (String) -> Unit,
    startTime: String, onStartTimeChange: (String) -> Unit,
    endTime: String, onEndTimeChange: (String) -> Unit,
    availableUsers: List<Profile>,
    selectedUserIds: Set<String>,
    onUserSelectionChanged: (String, Boolean) -> Unit,
    currentMembers: List<Profile> = emptyList(),
    onRemoveMember: (String) -> Unit = {},
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = Primary) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = groupName, onValueChange = onGroupNameChange, label = { Text("Group Name") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(value = lat, onValueChange = onLatChange, label = { Text("Latitude") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(value = long, onValueChange = onLongChange, label = { Text("Longitude") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(value = radius, onValueChange = onRadiusChange, label = { Text("Radius (meters)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(12.dp))
                Row {
                    OutlinedTextField(value = startTime, onValueChange = onStartTimeChange, label = { Text("Start Time (HH:mm)") }, modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(value = endTime, onValueChange = onEndTimeChange, label = { Text("End Time (HH:mm)") }, modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(16.dp))

                if (isEdit) {
                    Text("Current Members:", style = MaterialTheme.typography.titleSmall, color = Primary)
                    currentMembers.forEach { member ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(member.name ?: "Unknown", modifier = Modifier.weight(1f))
                            IconButton(onClick = { onRemoveMember(member.id) }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove Member")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Text("Available Members:", style = MaterialTheme.typography.titleSmall, color = Primary)
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .height(200.dp)
                        .fillMaxWidth()
                        .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
                ) {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        if (availableUsers.isEmpty()) {
                            Text(
                                "No unassigned users available",
                                modifier = Modifier.padding(16.dp),
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            availableUsers.forEach { user ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onUserSelectionChanged(user.id, !selectedUserIds.contains(user.id)) }
                                        .padding(horizontal = 8.dp, vertical = 8.dp)
                                ) {
                                    Checkbox(
                                        checked = selectedUserIds.contains(user.id),
                                        onCheckedChange = { isSelected -> onUserSelectionChanged(user.id, isSelected) }
                                    )
                                    Text(
                                        text = user.name ?: "Unknown",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f), thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AdminProfileScreen(navController: NavController) {
    UserProfileScreen(navController)
}
