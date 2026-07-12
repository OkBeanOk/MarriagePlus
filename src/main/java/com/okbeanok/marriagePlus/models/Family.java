package com.okbeanok.marriagePlus.models;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Family {

	private final String id;
	private final UUID parentOne;
	private final UUID parentTwo;
	private String name;
	private final Set<UUID> members;
	private final Set<UUID> adoptedChildren;
	private final Set<UUID> formerMembers;
	private final Map<UUID, Set<UUID>> childParents;
	private final long createdAt;

	public Family(String id, UUID parentOne, UUID parentTwo, String name, Set<UUID> members, long createdAt) {
		this(
				id,
				parentOne,
				parentTwo,
				name,
				members,
				new HashSet<>(),
				new HashSet<>(),
				new HashMap<>(),
				createdAt
		);
	}

	public Family(
			String id,
			UUID parentOne,
			UUID parentTwo,
			String name,
			Set<UUID> members,
			Set<UUID> adoptedChildren,
			Set<UUID> formerMembers,
			Map<UUID, Set<UUID>> childParents,
			long createdAt
	) {
		this.id = id;
		this.parentOne = parentOne;
		this.parentTwo = parentTwo;
		this.name = name;
		this.members = new HashSet<>(members);
		this.adoptedChildren = new HashSet<>(adoptedChildren);
		this.formerMembers = new HashSet<>(formerMembers);
		this.childParents = new HashMap<>();

		for (Map.Entry<UUID, Set<UUID>> entry : childParents.entrySet()) {
			this.childParents.put(entry.getKey(), new HashSet<>(entry.getValue()));
		}

		this.createdAt = createdAt;
		this.members.add(parentOne);
		this.members.add(parentTwo);
	}

	public String id() {
		return id;
	}

	public UUID parentOne() {
		return parentOne;
	}

	public UUID parentTwo() {
		return parentTwo;
	}

	public String name() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Set<UUID> members() {
		return members;
	}

	public Set<UUID> adoptedChildren() {
		return adoptedChildren;
	}

	public Set<UUID> formerMembers() {
		return formerMembers;
	}

	public Map<UUID, Set<UUID>> childParents() {
		return childParents;
	}

	public long createdAt() {
		return createdAt;
	}

	public boolean isParent(UUID uuid) {
		return parentOne.equals(uuid) || parentTwo.equals(uuid);
	}

	public boolean isMember(UUID uuid) {
		return members.contains(uuid);
	}

	public void addChild(UUID childId) {
		members.add(childId);
		adoptedChildren.add(childId);

		childParents.computeIfAbsent(childId, ignored -> new HashSet<>()).add(parentOne);
		childParents.computeIfAbsent(childId, ignored -> new HashSet<>()).add(parentTwo);
	}

	public void removeMember(UUID memberId, boolean preserveHistory) {
		members.remove(memberId);

		if (preserveHistory) {
			formerMembers.add(memberId);
		}
	}

	public boolean isChildOf(UUID childId, UUID parentId) {
		Set<UUID> parents = childParents.get(childId);
		return parents != null && parents.contains(parentId);
	}

	public boolean areSiblings(UUID first, UUID second) {
		Set<UUID> firstParents = childParents.get(first);
		Set<UUID> secondParents = childParents.get(second);

		if (firstParents == null || secondParents == null) {
			return false;
		}

		for (UUID parentId : firstParents) {
			if (secondParents.contains(parentId)) {
				return true;
			}
		}

		return false;
	}
}